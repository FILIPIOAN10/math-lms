package ro.mathlms.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ro.mathlms.ingest.ChapterSuggester.Choice;
import ro.mathlms.ingest.ChapterSuggester.Question;
import ro.mathlms.ingest.ChapterSuggester.Suggestion;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The OpenAI chapter suggester against a mock server: text only, the chapter list in the prompt, answers matched back. */
class OpenAiChapterSuggesterTest {

    private static final List<Choice> CHAPTERS = List.of(
            new Choice("A1", "Ordinea efectuării operațiilor", "calcul cu paranteze și puteri"),
            new Choice("G8", "Corpuri geometrice: arii și volume", "cub, prismă, volum"),
            new Choice("G9", "Drepte și plane", "distanța de la un punct la un plan"));

    private static final List<Question> QUESTIONS = List.of(
            new Question("SUBIECTUL I", "Rezultatul calculului $36 : 4 - 4 \\cdot 2$ este:", List.of("17", "1"), List.of()),
            new Question("SUBIECTUL al III-lea", "Cubul $ABCDA'B'C'D'$ are $AB = 8$ cm.", List.of(),
                    List.of("Arată că volumul este $512$.", "Distanța de la $D'$ la planul $(MNP)$.")));

    private MockRestServiceServer server;
    private OpenAiChapterSuggester suggester;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        suggester = new OpenAiChapterSuggester(builder.build(), new ObjectMapper(), "sk-test", "gpt-6-luna", "low");
    }

    private static String answer(String text) {
        return """
                {"id": "resp_2", "status": "completed", "model": "gpt-6-luna",
                 "output": [{"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": %s}]}]}
                """.formatted(new ObjectMapper().valueToTree(text).toString());
    }

    @Test
    void sendsTheQuestionsAsTextWithTheChapterListAndReadsOneChapterPerQuestionAndRequirement() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.instructions").value(containsString("G8 — Corpuri geometrice: arii și volume")))
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
                .andExpect(jsonPath("$.input[0].content[0].text").value(containsString("Întrebarea 1")))
                .andExpect(jsonPath("$.input[0].content[0].text").value(containsString("b) Distanța de la")))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess(answer("""
                        {"items": [
                          {"index": 1, "code": "A1", "reason": "Un calcul cu împărțire și înmulțire.", "parts": []},
                          {"index": 2, "code": "G8", "reason": "Un cub.", "parts": [
                            {"code": "G8", "reason": "Volumul cubului."},
                            {"code": "X7", "reason": "Cod inventat."}
                          ]}
                        ]}"""), MediaType.APPLICATION_JSON));

        List<Suggestion> suggestions = suggester.suggest(QUESTIONS, CHAPTERS);

        server.verify();
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).code()).isEqualTo("A1");
        assertThat(suggestions.get(0).reason()).isEqualTo("Un calcul cu împărțire și înmulțire.");
        assertThat(suggestions.get(1).parts()).hasSize(2);
        assertThat(suggestions.get(1).parts().get(0).code()).isEqualTo("G8");
        assertThat(suggestions.get(1).parts().get(1).code()).isNull(); // not a chapter we have: no suggestion
    }

    @Test
    void aQuestionTheModelSkippedHasNoSuggestion() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(answer("""
                        {"items": [{"index": 2, "code": "G8", "reason": "Un cub.", "parts": []}]}"""),
                        MediaType.APPLICATION_JSON));

        List<Suggestion> suggestions = suggester.suggest(QUESTIONS, CHAPTERS);

        assertThat(suggestions.get(0).code()).isNull();
        assertThat(suggestions.get(1).code()).isEqualTo("G8");
        assertThat(suggestions.get(1).parts()).hasSize(2).allMatch(p -> p.code() == null);
    }

    @Test
    void aFailureIsWordedForTheTeacher() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> suggester.suggest(QUESTIONS, CHAPTERS))
                .isInstanceOf(ExtractionException.class)
                .hasMessage(OpenAiPageExtractor.RATE_LIMITED);
    }

    @Test
    void withoutAKeyNothingIsSent() {
        OpenAiChapterSuggester noKey = new OpenAiChapterSuggester(RestClient.create(), new ObjectMapper(), " ", "m", "low");

        assertThatThrownBy(() -> noKey.suggest(QUESTIONS, CHAPTERS)).hasMessage(OpenAiPageExtractor.NO_KEY);
    }
}
