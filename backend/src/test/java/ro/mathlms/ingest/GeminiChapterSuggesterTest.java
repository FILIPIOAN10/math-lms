package ro.mathlms.ingest;

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

/** The Gemini chapter suggester against a mock server: the same prompt and reading as OpenAI's, text only. */
class GeminiChapterSuggesterTest {

    private static final String URL = "https://aiplatform.googleapis.com/v1/projects/proj-1/locations/global"
            + "/publishers/google/models/gemini-3.8-flash:generateContent";

    private static final List<Choice> CHAPTERS = List.of(
            new Choice("A1", "Ordinea efectuării operațiilor", "calcul cu paranteze și puteri"),
            new Choice("G8", "Corpuri geometrice: arii și volume", "cub, prismă, volum"));

    private static final List<Question> QUESTIONS = List.of(
            new Question("SUBIECTUL I", "Rezultatul calculului $36 : 4 - 4 \\cdot 2$ este:", List.of("17", "1"), List.of()),
            new Question("SUBIECTUL al III-lea", "Cubul $ABCDA'B'C'D'$ are $AB = 8$ cm.", List.of(),
                    List.of("Arată că volumul este $512$.", "Aria laterală.")));

    private MockRestServiceServer server;
    private GeminiChapterSuggester suggester;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GeminiClient.baseUrl("global"));
        server = MockRestServiceServer.bindTo(builder).build();
        suggester = new GeminiChapterSuggester(
                new GeminiClient(builder.build(), () -> "ya29.test", "proj-1", "global", "gemini-3.8-flash"),
                new ObjectMapper());
    }

    private static String answer(String text) {
        return """
                {"candidates": [{"content": {"role": "model", "parts": [{"text": %s}]}, "finishReason": "STOP"}],
                 "usageMetadata": {"promptTokenCount": 900, "candidatesTokenCount": 120},
                 "modelVersion": "gemini-3.8-flash"}
                """.formatted(new ObjectMapper().valueToTree(text).toString());
    }

    @Test
    void sendsTheQuestionsAsTextWithTheChapterListAndReadsTheProposals() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ya29.test"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(ChapterReading.instructions(CHAPTERS)))
                .andExpect(jsonPath("$.contents[0].parts.length()").value(1))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(ChapterReading.questionsText(QUESTIONS)))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.items.type").value("array"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(GeminiChapterSuggester.MAX_OUTPUT_TOKENS))
                .andRespond(withSuccess(answer("""
                        {"items": [
                          {"index": 1, "code": "a1", "reason": "Un calcul cu împărțire și înmulțire.", "parts": []},
                          {"index": 2, "code": "G8", "reason": "Un cub.", "parts": [
                            {"code": "G8", "reason": "Volumul cubului."},
                            {"code": "X7", "reason": "Cod inventat."}
                          ]}
                        ]}"""), MediaType.APPLICATION_JSON));

        List<Suggestion> suggestions = suggester.suggest(QUESTIONS, CHAPTERS);

        server.verify();
        assertThat(suggester.provider()).isEqualTo("gemini");
        assertThat(suggestions).extracting(Suggestion::code).containsExactly("A1", "G8");
        assertThat(suggestions.get(1).parts()).extracting(ChapterSuggester.PartSuggestion::code)
                .containsExactly("G8", null);
    }

    @Test
    void providerErrorsAreWordedForTheTeacher() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> suggester.suggest(QUESTIONS, CHAPTERS)).hasMessage(GeminiClient.RATE_LIMITED);
    }
}
