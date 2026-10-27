package ro.mathlms.ingest;

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
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The Gemini (Vertex AI) reader against a mock server: what is sent and how every answer is read. */
class GeminiPageExtractorTest {

    private static final String URL = "https://aiplatform.googleapis.com/v1/projects/proj-1/locations/global"
            + "/publishers/google/models/gemini-3.8-flash:generateContent";
    private static final List<PageImage> PAGES = List.of(
            new PageImage(new byte[]{1, 2, 3}, "image/jpeg"), new PageImage(new byte[]{4, 5}, "image/png"));

    private MockRestServiceServer server;
    private RestClient restClient;
    private GeminiPageExtractor extractor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GeminiClient.baseUrl("global"));
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        extractor = extractor(() -> "ya29.test", "proj-1");
    }

    private GeminiPageExtractor extractor(GeminiClient.AccessToken token, String project) {
        return new GeminiPageExtractor(new GeminiClient(restClient, token, project, "global", "gemini-3.8-flash"),
                new ObjectMapper());
    }

    /** A Vertex answer whose visible text is {@code text}, after a thought part that must be ignored. */
    private static String answer(String text, String finishReason) {
        return """
                {"candidates": [{"content": {"role": "model", "parts": [
                    {"thought": true, "text": "Let me look at page 1..."},
                    {"text": %s}]},
                  "finishReason": "%s"}],
                 "usageMetadata": {"promptTokenCount": 2100, "candidatesTokenCount": 500, "thoughtsTokenCount": 140,
                                   "totalTokenCount": 2740},
                 "modelVersion": "gemini-3.8-flash"}
                """.formatted(new ObjectMapper().valueToTree(text).toString(), finishReason);
    }

    @Test
    void sendsThePagesWithTheSchemaAndReadsTheQuestions() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ya29.test"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(PageReading.INSTRUCTIONS))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Pagina 1"))
                .andExpect(jsonPath("$.contents[0].parts[1].inlineData.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.contents[0].parts[1].inlineData.data").value("AQID"))
                .andExpect(jsonPath("$.contents[0].parts[2].text").value("Pagina 2"))
                .andExpect(jsonPath("$.contents[0].parts[3].inlineData.mimeType").value("image/png"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.items.items.required.length()").value(11))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(GeminiPageExtractor.MAX_OUTPUT_TOKENS))
                // copying text needs no reasoning: thinking is billed as output
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("LOW"))
                .andRespond(withSuccess(answer("""
                        {"items": [
                          {"page": 1, "section": "SUBIECTUL I", "type": "SINGLE_CHOICE", "statement": "Cât face $2+3$?",
                           "options": ["4", "5", "6", "7"], "points": 5, "hasFigure": false, "warnings": [], "parts": []},
                          {"page": 2, "section": "SUBIECTUL al III-lea", "type": "OPEN", "statement": "Se consideră $E(x)$.",
                           "options": [], "points": 5, "hasFigure": false, "warnings": [],
                           "parts": [{"statement": "Arată că $E(1)=2$.", "points": 2},
                                     {"statement": "Rezolvă ecuația $E(x)=0$.", "points": 3}]}
                        ]}""", "STOP"), MediaType.APPLICATION_JSON));

        ExtractionResult result = extractor.extract(PAGES);

        server.verify();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).type()).isEqualTo(QuizItemType.SINGLE_CHOICE);
        assertThat(result.items().get(0).options()).containsExactly("4", "5", "6", "7");
        assertThat(result.items().get(1).parts()).extracting(ExtractedItem.Part::points).containsExactly(2, 3);
        assertThat(result.model()).isEqualTo("gemini-3.8-flash");
        assertThat(result.inputTokens()).isEqualTo(2100);
        assertThat(result.outputTokens()).isEqualTo(640); // thinking is billed as output too
        assertThat(extractor.provider()).isEqualTo("gemini");
    }

    @Test
    void aRegionalLocationUsesItsOwnHost() {
        assertThat(GeminiClient.baseUrl("global")).isEqualTo("https://aiplatform.googleapis.com");
        assertThat(GeminiClient.baseUrl(" ")).isEqualTo("https://aiplatform.googleapis.com");
        assertThat(GeminiClient.baseUrl("europe-west1")).isEqualTo("https://europe-west1-aiplatform.googleapis.com");
    }

    @Test
    void noCredentialsOrNoProjectMeansNoCall() {
        GeminiPageExtractor keyless = extractor(() -> {
            throw new ExtractionException(GoogleAccessTokens.NO_KEY);
        }, "proj-1");
        assertThatThrownBy(() -> keyless.extract(PAGES)).hasMessage(GoogleAccessTokens.NO_KEY);

        assertThatThrownBy(() -> extractor(() -> "ya29.test", " ").extract(PAGES)).hasMessage(GeminiClient.NO_PROJECT);
        server.verify();
    }

    @Test
    void aCutOffAnswerIsNotParsed() {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"items\": [{\"pa", "MAX_TOKENS"),
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.TOO_LONG);
    }

    @Test
    void aBlockedRequestOrAnswerIsARefusal() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\": {\"blockReason\": \"PROHIBITED_CONTENT\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.REFUSED);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("", "SAFETY"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.REFUSED);
    }

    @Test
    void anAnswerThatIsNotTheSchemaFailsPlainly() {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("nu e JSON", "STOP"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.UNREADABLE_ANSWER);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\": []}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(PageReading.UNREADABLE_ANSWER);
    }

    /** A reader whose client retries a busy Google right away (the real pauses are 10, 30 and 60 s). */
    private GeminiPageExtractor retrying() {
        return new GeminiPageExtractor(new GeminiClient(restClient, () -> "ya29.test", "proj-1", "global", "gemini-3.8-flash",
                List.of(java.time.Duration.ZERO, java.time.Duration.ZERO, java.time.Duration.ZERO)), new ObjectMapper());
    }

    @Test
    void aBusyGoogleIsAskedAgainBeforeTheTeacherSeesAnError() throws Exception {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\": {\"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\"}}"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{\"items\": []}", "STOP"), MediaType.APPLICATION_JSON));

        assertThat(retrying().extract(PAGES).items()).isEmpty();
        server.verify();
    }

    @Test
    void afterTheLastRetryTheTeacherIsToldGoogleIsBusy() {
        for (int i = 0; i < 4; i++) {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        assertThatThrownBy(() -> retrying().extract(PAGES)).hasMessage(GeminiClient.RATE_LIMITED);
        server.verify(); // the first try and three more, no fifth
    }

    @Test
    void aRequestGoogleRejectsIsNotSentAgain() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> retrying().extract(PAGES)).hasMessage(GeminiClient.REJECTED);
        server.verify();
    }

    @Test
    void httpErrorsBecomeReasonsTheTeacherUnderstands() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("{\"error\": {\"code\": 403, \"status\": \"PERMISSION_DENIED\"}}"));
        assertThatThrownBy(() -> extractor.extract(PAGES)).hasMessage(GeminiClient.NO_ACCESS);

        assertThat(GeminiClient.messageFor(HttpStatus.UNAUTHORIZED)).isEqualTo(GeminiClient.NO_ACCESS);
        assertThat(GeminiClient.messageFor(HttpStatus.NOT_FOUND)).isEqualTo(GeminiClient.NO_MODEL);
        assertThat(GeminiClient.messageFor(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(GeminiClient.RATE_LIMITED);
        assertThat(GeminiClient.messageFor(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(GeminiClient.UNAVAILABLE);
        assertThat(GeminiClient.messageFor(HttpStatus.BAD_REQUEST)).isEqualTo(GeminiClient.REJECTED);
    }
}
