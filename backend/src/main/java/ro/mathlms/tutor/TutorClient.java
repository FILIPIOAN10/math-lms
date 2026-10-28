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
 * Calls the tutor service ({@code POST /help}) with the shared token and returns its answer as is. The service checks
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
        if (token.isEmpty()) {
            throw new TutorException(NOT_CONFIGURED);
        }
        Answer answer;
        try {
            answer = restClient.post()
                    .uri("/help")
                    .header("X-Tutor-Token", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Request(statement, slot, levels))
                    .retrieve()
                    .body(Answer.class);
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
        if (answer == null || answer.steps() == null) {
            throw new TutorException(FAILED);
        }
        return answer;
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
