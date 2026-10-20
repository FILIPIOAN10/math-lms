package ro.mathlms.ingest;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ro.mathlms.ingest.PageExtractor.ExtractionResult;
import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.QuizItemType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The OpenAI reader against a mock server: what is sent (pages, schema, no storage) and how every answer is read. */
class OpenAiPageExtractorTest {

    private static final List<PageImage> PAGES = List.of(
            new PageImage(new byte[]{1, 2, 3}, "image/jpeg"), new PageImage(new byte[]{4, 5}, "image/png"));

    private MockRestServiceServer server;
    private OpenAiPageExtractor extractor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        extractor = new OpenAiPageExtractor(builder.build(), new ObjectMapper(), " sk-test ", "gpt-6-luna", "low", "high");
    }

    private static String answer(String text) {
        return """
                {"id": "resp_1", "status": "completed", "model": "gpt-6-luna",
                 "output": [
                   {"type": "reasoning", "summary": []},
                   {"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": %s}]}
                 ],
                 "usage": {"input_tokens": 2100, "output_tokens": 640}}
                """.formatted(new ObjectMapper().valueToTree(text).toString());
    }

    @Test
    void sendsThePagesWithTheSchemaAndReadsTheQuestions() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-6-luna"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.reasoning.effort").value("low"))
                .andExpect(jsonPath("$.input[0].content[0].text").value("Pagina 1"))
                .andExpect(jsonPath("$.input[0].content[1].image_url").value("data:image/jpeg;base64,AQID"))
                .andExpect(jsonPath("$.input[0].content[1].detail").value("high"))
                .andExpect(jsonPath("$.input[0].content[3].image_url").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.properties.items.items.required.length()").value(11))
                .andRespond(withSuccess(answer("""
                        {"items": [
                          {"page": 1, "section": "SUBIECTUL I", "type": "SINGLE_CHOICE", "statement": "Cât face $2+3$?",
                           "options": ["4", "5", "6"], "points": 5, "hasFigure": false, "warnings": []},
                          {"page": 2, "section": null, "type": "OPEN", "statement": "Calculează aria.", "options": [],
                           "points": null, "hasFigure": true, "warnings": ["Un cuvânt ilizibil."]}
                        ]}"""), MediaType.APPLICATION_JSON));

        ExtractionResult result = extractor.extract(PAGES);

        server.verify();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).type()).isEqualTo(QuizItemType.SINGLE_CHOICE);
        assertThat(result.items().get(0).options()).containsExactly("4", "5", "6");
        assertThat(result.items().get(1).points()).isNull();
        assertThat(result.items().get(1).hasFigure()).isTrue();
        assertThat(result.model()).isEqualTo("gpt-6-luna");
        assertThat(result.inputTokens()).isEqualTo(2100);
        assertThat(result.outputTokens()).isEqualTo(640);
    }

    @Test
    void noKeyMeansNoCall() {
        OpenAiPageExtractor keyless = new OpenAiPageExtractor(RestClient.create(), new ObjectMapper(), "", "m", "low", "high");

        assertThatThrownBy(() -> keyless.extract(PAGES)).hasMessage(OpenAiPageExtractor.NO_KEY);
    }

    @Test
    void aRefusalIsNotReadAsQuestions() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("""
                {"status": "completed", "output": [{"type": "message", "content": [{"type": "refusal", "refusal": "No."}]}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.REFUSED);
    }

    @Test
    void aCutOffAnswerIsNotParsed() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("""
                {"status": "incomplete", "incomplete_details": {"reason": "max_output_tokens"},
                 "output": [{"type": "message", "content": [{"type": "output_text", "text": "{\\"items\\": [{\\"pa"}]}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.TOO_LONG);
    }

    @Test
    void anAnswerThatIsNotTheSchemaFailsPlainly() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(answer("nu e JSON"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.UNREADABLE_ANSWER);
    }

    @Test
    void httpErrorsBecomeReasonsTheTeacherUnderstands() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\": {\"message\": \"Incorrect API key\"}}"));
        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(OpenAiPageExtractor.BAD_KEY);

        assertThat(OpenAiPageExtractor.messageFor(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(OpenAiPageExtractor.RATE_LIMITED);
        assertThat(OpenAiPageExtractor.messageFor(HttpStatus.BAD_GATEWAY)).isEqualTo(OpenAiPageExtractor.UNAVAILABLE);
        assertThat(OpenAiPageExtractor.messageFor(HttpStatus.BAD_REQUEST)).isEqualTo(OpenAiPageExtractor.REJECTED);
    }
}
