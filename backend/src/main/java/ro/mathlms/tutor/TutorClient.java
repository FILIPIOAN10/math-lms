package ro.mathlms.tutor;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * Calls the tutor service ({@code POST /help}, {@code POST /explain}) with the shared token and returns its answer as is. The service checks
 * the token, retrieves the textbook passages and writes the levels; this side only says who may ask and for what.
 * Every failure becomes a {@link TutorException} worded for the teacher - the service's own text only for a 502,
 * whose detail it writes for people ("Modelul nu a întors JSON-ul așteptat.").
 */
@Component
public class TutorClient {

    private static final Logger log = LoggerFactory.getLogger(TutorClient.class);

    static final String NOT_CONFIGURED = "Tutorul nu e configurat pe server (TUTOR_SERVICE_TOKEN).";
    static final String UNREACHABLE = "Tutorul nu răspunde acum. Încearcă din nou peste câteva minute.";
    static final String TOO_SLOW = "Tutorul a lucrat prea mult la această întrebare. Încearcă din nou.";
    static final String FAILED = "Tutorul nu a putut scrie indiciile. Încearcă din nou.";

    /** What the service sends back (snake_case): levels with their pages, or why it stopped. */
    public record Answer(boolean answered, List<Step> steps, Refusal refusal, String strategy,
                         @JsonProperty("best_distance") Double bestDistance,
                         @JsonProperty("input_tokens") int inputTokens,
                         @JsonProperty("output_tokens") int outputTokens,
                         @JsonProperty("cost_usd") double costUsd) {
    }

    public record Step(int level, String text, List<Source> sources) {
    }

    public record Source(int n, String code, String title, @JsonProperty("book_page") int bookPage) {
    }

    /** {@code reason}: no_passages, too_far, not_covered or no_citation; {@code message}: "Nu am asta în manual." */
    public record Refusal(String reason, String message) {
    }

    record Request(String statement, String slot, int levels) {
    }

    /** One earlier message of the student's box, oldest first: {@code role} is "user" or "bot". */
    public record Turn(String role, String text) {
    }

    record ExplainRequest(String question, List<Turn> history) {
    }

    /**
     * What {@code POST /explain} sends back: two or three sentences from the lessons with the pages they rely on, or a
     * refusal; {@code question} is the standalone question it searched for (a follow-up rewritten with the history).
     */
    public record Explanation(boolean answered, String text, List<Source> sources, Refusal refusal, String question,
                              boolean rewritten,
                              @JsonProperty("input_tokens") int inputTokens,
                              @JsonProperty("output_tokens") int outputTokens,
                              @JsonProperty("cost_usd") double costUsd) {
    }

    record MistakeRequest(String statement, String slot, String chosen, String correct) {
    }

    /** What {@code POST /mistake} sends back: a draft for the teacher of why an option is wrong, or a refusal. */
    public record MistakeDraft(boolean answered, String text, List<Source> sources, Refusal refusal,
                               @JsonProperty("input_tokens") int inputTokens,
                               @JsonProperty("output_tokens") int outputTokens,
                               @JsonProperty("cost_usd") double costUsd) {
    }

    /** What {@code POST /similar-index} did: statements embedded now, left as they were, rows dropped. */
    public record IndexReport(int inserted, int unchanged, int removed) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String token;

    @Autowired
    public TutorClient(RestClient.Builder builder, ObjectMapper objectMapper, TutorProperties properties) {
        this(builder.clone().baseUrl(properties.baseUrl()).requestFactory(timeouts(properties.timeout())).build(),
                objectMapper, properties.token());
    }

    /** For tests: a client bound to a mock server. */
    TutorClient(RestClient restClient, ObjectMapper objectMapper, String token) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.token = token == null ? "" : token.strip();
    }

    private static SimpleClientHttpRequestFactory timeouts(Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(read);
        return factory;
    }

    /** Up to three levels for one exercise; {@code slot} is its exam position ("I.1"), or null to search by meaning. */
    public Answer help(String statement, String slot, int levels) {
        Answer answer = post("/help", new Request(statement, slot, levels), Answer.class);
        if (answer == null || answer.steps() == null) {
            throw new TutorException(FAILED);
        }
        return answer;
    }

    /** The student's box: an explanation of {@code question} from the lessons, {@code history} for a follow-up. */
    public Explanation explain(String question, List<Turn> history) {
        Explanation answer = post("/explain", new ExplainRequest(question, history), Explanation.class);
        if (answer == null || answer.sources() == null) {
            throw new TutorException(FAILED);
        }
        return answer;
    }

    /** „De ce am greșit?": why {@code chosen} is wrong, from the lessons; {@code correct} may be null. */
    public MistakeDraft mistake(String statement, String slot, String chosen, String correct) {
        MistakeDraft answer = post("/mistake", new MistakeRequest(statement, slot, chosen, correct), MistakeDraft.class);
        if (answer == null || answer.sources() == null) {
            throw new TutorException(FAILED);
        }
        return answer;
    }

    /** „Dă-mi una asemănătoare": embeds the questions and examples that are new or changed since the last run. */
    public IndexReport indexSimilar() {
        IndexReport report = post("/similar-index", java.util.Map.of(), IndexReport.class);
        if (report == null) {
            throw new TutorException(FAILED);
        }
        return report;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        if (token.isEmpty()) {
            throw new TutorException(NOT_CONFIGURED);
        }
        try {
            return restClient.post()
                    .uri(path)
                    .header("X-Tutor-Token", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Tutor answered {}: {}", status, e.getResponseBodyAsString());
            throw new TutorException(switch (status) {
                case 401, 503 -> NOT_CONFIGURED;
                case 502 -> detailOr(e.getResponseBodyAsString(), FAILED);
                default -> FAILED;
            }, e);
        } catch (ResourceAccessException e) {
            log.warn("Tutor could not be reached: {}", e.getMessage());
            throw new TutorException(e.getCause() instanceof java.net.SocketTimeoutException ? TOO_SLOW : UNREACHABLE, e);
        }
    }

    private String detailOr(String body, String fallback) {
        try {
            String detail = objectMapper.readTree(body).path("detail").asString("");
            return detail.isBlank() ? fallback : detail;
        } catch (Exception e) {
            return fallback;
        }
    }
}
