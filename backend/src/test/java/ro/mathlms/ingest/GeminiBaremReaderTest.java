package ro.mathlms.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ro.mathlms.ingest.BaremReader.OutlineItem;
import ro.mathlms.ingest.BaremReader.OutlinePart;
import ro.mathlms.ingest.BaremReader.Result;
import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.QuizItemType;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The Gemini barem reader against a mock server: the strong model, the photos then the test's questions, the tidying. */
class GeminiBaremReaderTest {

    private static final String URL = "https://aiplatform.googleapis.com/v1/projects/proj-1/locations/global"
            + "/publishers/google/models/gemini-4-argon:generateContent";
    private static final List<OutlineItem> QUESTIONS = List.of(
            new OutlineItem("I.1", QuizItemType.SINGLE_CHOICE, "Cât face $6 - 6 : 2$?", List.of("0", "3", "6", "9"), 5, List.of()),
            new OutlineItem("III.1", QuizItemType.OPEN, "Un teren dreptunghiular.", List.of(), 5,
                    List.of(new OutlinePart("a", "Calculează perimetrul.", 2), new OutlinePart("b", "Arată că aria > 900.", 3))));

    private MockRestServiceServer server;
    private GeminiBaremReader reader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GeminiClient.baseUrl("global"));
        server = MockRestServiceServer.bindTo(builder).build();
        // the client's default model is the cheap one: the reader must ask for the strong one
        GeminiClient client = new GeminiClient(builder.build(), () -> "ya29.test", "proj-1", "global", "gemini-3.8-flash");
        reader = new GeminiBaremReader(client, new ObjectMapper(), "gemini-4-argon", "LOW");
    }

    private static String answer(String json) {
        return """
                {"candidates": [{"content": {"parts": [{"text": %s}]}, "finishReason": "STOP"}],
                 "usageMetadata": {"promptTokenCount": 3000, "candidatesTokenCount": 800, "thoughtsTokenCount": 4000},
                 "modelVersion": "gemini-4-argon"}
                """.formatted(new ObjectMapper().valueToTree(json).toString());
    }

    @Test
    void sendsThePhotosThenTheQuestionsToTheStrongModelAndTidiesTheAnswer() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(BaremReading.INSTRUCTIONS))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Pagina 1"))
                .andExpect(jsonPath("$.contents[0].parts[1].inlineData.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.contents[0].parts[2].text").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("I.1 | grilă | 5p | Cât face $6 - 6 : 2$? | variante: a) 0; b) 3; c) 6; d) 9"),
                        org.hamcrest.Matchers.containsString("III.1 | cu rezolvare | 5p | Un teren dreptunghiular. | cerințe: a) (2p)"))))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.properties.entries.items.required.length()").value(5))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("LOW"))
                .andRespond(withSuccess(answer("""
                        {"entries": [
                          {"label": "I.1", "correctLetter": "B)", "barem": null, "parts": [], "warnings": []},
                          {"label": "III.1", "correctLetter": null, "barem": " ", "warnings": ["Puncte diferite."],
                           "parts": [{"label": "a)", "barem": "$P = 2(L + l)$ ... 1p"}, {"label": "b", "barem": ""}]},
                          {"label": "", "correctLetter": "c", "barem": null, "parts": [], "warnings": []}
                        ]}"""), MediaType.APPLICATION_JSON));

        Result result = reader.read(List.of(new PageImage(new byte[]{1, 2, 3}, "image/jpeg")), QUESTIONS);

        server.verify();
        assertThat(result.model()).isEqualTo("gemini-4-argon");
        assertThat(result.outputTokens()).isEqualTo(4800); // thinking is billed as output
        assertThat(result.entries()).hasSize(2); // the unlabelled one is dropped
        assertThat(result.entries().get(0).correctLetter()).isEqualTo("b");
        BaremReader.Entry problem = result.entries().get(1);
        assertThat(problem.barem()).isNull();
        assertThat(problem.parts()).containsExactly(new BaremReader.PartBarem("a", "$P = 2(L + l)$ ... 1p"));
        assertThat(problem.warnings()).containsExactly("Puncte diferite.");
    }

    @Test
    void anAnswerThatIsNotTheJsonIsAReadableFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("nu e json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> reader.read(List.of(new PageImage(new byte[]{1}, "image/png")), QUESTIONS))
                .isInstanceOf(ExtractionException.class).hasMessage(PageReading.UNREADABLE_ANSWER);
    }

    @Test
    void aBlankThinkingLevelLeavesTheModelsDefault() {
        GeminiClient client = new GeminiClient(RestClient.create(), () -> "ya29.test", "proj-1", "global", "gemini-3.8-flash");

        assertThat(new GeminiBaremReader(client, new ObjectMapper(), "gemini-4-argon", "")
                .request(List.of(new PageImage(new byte[]{1}, "image/png")), QUESTIONS)
                .path("generationConfig").has("thinkingConfig")).isFalse();
    }
}
