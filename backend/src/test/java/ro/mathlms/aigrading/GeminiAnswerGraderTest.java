package ro.mathlms.aigrading;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ro.mathlms.aigrading.AnswerGrader.Proposal;
import ro.mathlms.aigrading.AnswerGrader.Task;
import ro.mathlms.aigrading.AnswerGrader.TaskPart;
import ro.mathlms.ingest.GeminiClient;
import ro.mathlms.ingest.PageExtractor.PageImage;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The Gemini grader against a mock server: the strong model, the problem first, then the figure and the pages. */
class GeminiAnswerGraderTest {

    private static final String URL = "https://aiplatform.googleapis.com/v1/projects/proj-1/locations/global"
            + "/publishers/google/models/gemini-4-argon:generateContent";

    @Test
    void sendsTheProblemTheFigureAndThePagesToTheStrongModel() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(GeminiClient.baseUrl("global"));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiAnswerGrader grader = new GeminiAnswerGrader(
                new GeminiClient(builder.build(), () -> "ya29.test", "proj-1", "global", "gemini-3.8-flash"),
                new ObjectMapper(), "gemini-4-argon");
        Task task = new Task("Un teren.", 5, null,
                List.of(new TaskPart("a", "Perimetrul.", 2, "$P = 130$ ... 2p"), new TaskPart("b", "Aria.", 3, "... 3p")),
                new PageImage(new byte[]{9}, "image/png"),
                List.of(new PageImage(new byte[]{1, 2, 3}, "image/jpeg"), new PageImage(new byte[]{4}, "image/jpeg")));
        String answer = """
                {"points": 4, "reason": "Aproape complet.", "confidence": "MEDIUM", "readable": true,
                 "parts": [{"label": "a", "points": 2, "reason": "Corect."}, {"label": "b", "points": 2, "reason": "Fără concluzie."}]}""";

        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(GradingReading.INSTRUCTIONS))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(GradingReading.problem(task)))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value("Figura problemei:"))
                .andExpect(jsonPath("$.contents[0].parts[2].inlineData.mimeType").value("image/png"))
                .andExpect(jsonPath("$.contents[0].parts[3].text").value("Rezolvarea elevului:"))
                .andExpect(jsonPath("$.contents[0].parts[4].text").value("Pagina 1"))
                .andExpect(jsonPath("$.contents[0].parts[5].inlineData.data").value("AQID"))
                .andExpect(jsonPath("$.contents[0].parts[6].text").value("Pagina 2"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.required.length()").value(5))
                .andRespond(withSuccess("""
                        {"candidates": [{"content": {"parts": [{"text": %s}]}, "finishReason": "STOP"}],
                         "usageMetadata": {"promptTokenCount": 4100, "candidatesTokenCount": 300, "thoughtsTokenCount": 5200},
                         "modelVersion": "gemini-4-argon"}
                        """.formatted(new ObjectMapper().valueToTree(answer).toString()), MediaType.APPLICATION_JSON));

        Proposal proposal = grader.grade(task);

        server.verify();
        assertThat(proposal.points()).isEqualTo(4);
        assertThat(proposal.parts()).extracting(AnswerGrader.PartProposal::reason).containsExactly("Corect.", "Fără concluzie.");
        assertThat(proposal.model()).isEqualTo("gemini-4-argon");
        assertThat(proposal.inputTokens()).isEqualTo(4100);
        assertThat(proposal.outputTokens()).isEqualTo(5500);
    }
}
