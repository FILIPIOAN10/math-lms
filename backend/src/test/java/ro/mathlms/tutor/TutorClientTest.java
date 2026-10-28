package ro.mathlms.tutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The tutor client against a mock server: the token and the request it sends, the snake_case answer, the failures. */
class TutorClientTest {

    private static final String URL = "http://tutor:8001/help";

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://tutor:8001");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private TutorClient client(String token) {
        return new TutorClient(restClient, new ObjectMapper(), token);
    }

    @Test
    void sendsTheTokenAndTheExerciseAndReadsTheLevelsWithTheirPages() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Tutor-Token", "s3cret"))
                .andExpect(jsonPath("$.statement").value("Calculează 20 - 2·(7 - 2)."))
                .andExpect(jsonPath("$.slot").value("I.1"))
                .andExpect(jsonPath("$.levels").value(3))
                .andRespond(withSuccess("""
                        {"answered": true, "refusal": null, "strategy": "slot", "best_distance": null,
                         "steps": [{"level": 1, "text": "Întâi paranteza.",
                                    "sources": [{"n": 1, "code": "AIS5P5", "title": "Ordinea operațiilor", "book_page": 12}]}],
                         "passages": [{"n": 1, "code": "AIS5P5", "title": "Ordinea operațiilor", "book_page": 12, "distance": null}],
                         "input_tokens": 1200, "output_tokens": 300, "cost_usd": 0.00027}
                        """, MediaType.APPLICATION_JSON));

        TutorClient.Answer answer = client(" s3cret ").help("Calculează 20 - 2·(7 - 2).", "I.1", 3);

        server.verify();
        assertThat(answer.answered()).isTrue();
        assertThat(answer.steps()).singleElement().satisfies(step -> {
            assertThat(step.text()).isEqualTo("Întâi paranteza.");
            assertThat(step.sources()).containsExactly(new TutorClient.Source(1, "AIS5P5", "Ordinea operațiilor", 12));
        });
        assertThat(answer.inputTokens()).isEqualTo(1200);
        assertThat(answer.outputTokens()).isEqualTo(300);
        assertThat(answer.costUsd()).isEqualTo(0.00027);
    }

    @Test
    void withoutATokenItDoesNotCallTheService() {
        assertThatThrownBy(() -> client("").help("2 + 2", null, 3))
                .isInstanceOf(TutorException.class).hasMessage(TutorClient.NOT_CONFIGURED);
        server.verify(); // nothing was expected, nothing was sent
    }

    @Test
    void aRefusedTokenIsAConfigurationProblem() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client("wrong").help("2 + 2", null, 3))
                .isInstanceOf(TutorException.class).hasMessage(TutorClient.NOT_CONFIGURED);
    }

    @Test
    void aModelFailurePassesOnTheServicesOwnWords() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\": \"Modelul nu a întors JSON-ul așteptat.\"}"));

        assertThatThrownBy(() -> client("s3cret").help("2 + 2", null, 3))
                .isInstanceOf(TutorException.class).hasMessage("Modelul nu a întors JSON-ul așteptat.");
    }

    @Test
    void anyOtherErrorIsAGenericFailure() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("Traceback ..."));

        assertThatThrownBy(() -> client("s3cret").help("2 + 2", null, 3))
                .isInstanceOf(TutorException.class).hasMessage(TutorClient.FAILED);
    }
}
