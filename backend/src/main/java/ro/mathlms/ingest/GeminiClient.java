package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Gemini on Google Cloud (Vertex AI, now "Agent Platform"): one {@code generateContent} call, signed in as the service
 * account, and the reply reduced to its visible text and token counts. Shared by {@link GeminiPageExtractor} and
 * {@link GeminiChapterSuggester}, which only build the request body. Billed to the Google Cloud project, so a free-trial
 * credit pays for it (the AI Studio API is excluded from that credit).
 *
 * <p>Settings ({@code app.ai.gemini.*}): the project id, the location ({@code global}, or a region such as
 * {@code europe-west1}, which serves fewer models) and the model. Every failure becomes an {@link ExtractionException}
 * worded for the teacher.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    static final Duration TIMEOUT = Duration.ofSeconds(180);
    private static final String GLOBAL = "global";

    static final String NO_PROJECT = "Proiectul Google Cloud (GEMINI_PROJECT) lipsește din configurarea serverului.";
    static final String NO_ACCESS = "Contul Google nu are acces la Gemini: verifică rolul „Agent Platform user” și că "
            + "API-ul e pornit în proiect.";
    static final String NO_MODEL = "Modelul Gemini setat nu există în regiunea aleasă (GEMINI_MODEL, GEMINI_LOCATION).";
    static final String RATE_LIMITED = "Google e ocupat momentan și a refuzat cererea de mai multe ori. Încearcă din nou "
            + "peste câteva minute (pozele rămân salvate).";
    static final String UNAVAILABLE = "Google nu răspunde acum. Încearcă din nou peste câteva minute.";
    static final String REJECTED = "Google a respins cererea. Verifică pozele (format, mărime) și încearcă din nou.";

    /**
     * Pauses before asking again when Google answers "busy" (429 RESOURCE_EXHAUSTED: no free shared capacity at that
     * moment, not the credit) or a 5xx: Google's own advice is truncated exponential backoff. Only answers that come
     * back at once are retried - a timeout is not - so the worst case stays about two minutes over one read, well under
     * the 10 minutes after which a read is taken as stuck.
     */
    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60));
    private static final Set<Integer> RETRIED = Set.of(429, 500, 502, 503, 504);

    /** Finish reasons that mean "the model would not answer", not "the answer is broken". */
    private static final Set<String> BLOCKED = Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION");

    /** Where the bearer token comes from: {@link GoogleAccessTokens}, or a fixed string in tests. */
    @FunctionalInterface
    interface AccessToken {
        String get() throws ExtractionException;
    }

    /** The answer's visible text (thoughts left out) and what it cost; output tokens include the thinking. */
    record Reply(String text, String model, Integer inputTokens, Integer outputTokens) {
    }

    private final RestClient restClient;
    private final AccessToken accessToken;
    private final String project;
    private final String location;
    private final String model;
    private final List<Duration> backoff;

    @Autowired
    public GeminiClient(RestClient.Builder builder, GoogleAccessTokens accessTokens,
                        @Value("${app.ai.gemini.project:}") String project,
                        @Value("${app.ai.gemini.location:global}") String location,
                        @Value("${app.ai.gemini.model:gemini-3.8-flash}") String model) {
        this(builder.clone().baseUrl(baseUrl(location)).requestFactory(timeouts()).build(), accessTokens, project,
                location, model, BACKOFF);
    }

    /** For tests: a client bound to a mock server, asking once (no retries). */
    GeminiClient(RestClient restClient, AccessToken accessToken, String project, String location, String model) {
        this(restClient, accessToken, project, location, model, List.of());
    }

    /** For tests: a client bound to a mock server, with these pauses between tries (zero = at once). */
    GeminiClient(RestClient restClient, AccessToken accessToken, String project, String location, String model,
                 List<Duration> backoff) {
        this.backoff = List.copyOf(backoff);
        this.restClient = restClient;
        this.accessToken = accessToken;
        this.project = project == null ? "" : project.strip();
        this.location = location == null || location.isBlank() ? GLOBAL : location.strip();
        this.model = model;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    /** {@code global} has its own host; a region is served from {@code <region>-aiplatform.googleapis.com}. */
    static String baseUrl(String location) {
        return location == null || location.isBlank() || GLOBAL.equals(location.strip())
                ? "https://aiplatform.googleapis.com"
                : "https://" + location.strip() + "-aiplatform.googleapis.com";
    }

    String model() {
        return model;
    }

    /** Sends {@code body} (contents, systemInstruction, generationConfig) and returns Vertex's raw answer. */
    JsonNode generate(ObjectNode body) throws ExtractionException {
        if (project.isEmpty()) {
            throw new ExtractionException(NO_PROJECT);
        }
        String bearer = accessToken.get();
        JsonNode response;
        try {
            response = postWithRetries(body, bearer);
        } catch (RestClientResponseException e) {
            // The body explains a 400/404 (bad image, unknown model); it never contains the token.
            log.warn("Gemini answered {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExtractionException(messageFor(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            log.warn("Gemini could not be reached: {}", e.getMessage());
            throw new ExtractionException(e.getCause() instanceof java.net.SocketTimeoutException
                    ? PageReading.TOO_SLOW : UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        return response;
    }

    /** One call, asked again after each pause in {@link #backoff} while Google answers "busy". */
    private JsonNode postWithRetries(ObjectNode body, String bearer) throws ExtractionException {
        for (int attempt = 0; ; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1/projects/{project}/locations/{location}/publishers/google/models/{model}:generateContent",
                                project, location, model)
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                if (attempt >= backoff.size() || !RETRIED.contains(e.getStatusCode().value())) {
                    throw e;
                }
                Duration pause = backoff.get(attempt);
                log.info("Gemini answered {} (busy), asking again in {} s ({}/{})", e.getStatusCode().value(),
                        pause.toSeconds(), attempt + 1, backoff.size());
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ExtractionException(UNAVAILABLE, interrupted);
                }
            }
        }
    }

    static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401, 403 -> NO_ACCESS;
            case 404 -> NO_MODEL;
            case 429 -> RATE_LIMITED;
            default -> status.is5xxServerError() ? UNAVAILABLE : REJECTED;
        };
    }

    /** The first candidate's visible text; a block, a cut-off or an empty answer is an {@link ExtractionException}. */
    Reply reply(JsonNode response) throws ExtractionException {
        String blockReason = response.path("promptFeedback").path("blockReason").asString("");
        if (!blockReason.isEmpty()) {
            log.warn("Gemini blocked the request: {}", blockReason);
            throw new ExtractionException(PageReading.REFUSED);
        }
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            log.warn("Gemini answer without candidates");
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        String finishReason = candidate.path("finishReason").asString("");
        if ("MAX_TOKENS".equals(finishReason)) {
            log.warn("Gemini answer cut off at the output limit");
            throw new ExtractionException(PageReading.TOO_LONG);
        }
        if (BLOCKED.contains(finishReason)) {
            log.warn("Gemini stopped the answer: {}", finishReason);
            throw new ExtractionException(PageReading.REFUSED);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.path("text").isString()) {
                text.append(part.get("text").asString());
            }
        }
        if (text.isEmpty()) {
            log.warn("Gemini answer without text, finish reason {}", finishReason);
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        JsonNode usage = response.path("usageMetadata");
        Integer input = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
        Integer output = usage.has("candidatesTokenCount") || usage.has("thoughtsTokenCount")
                ? usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0)
                : null;
        return new Reply(text.toString(), response.path("modelVersion").asString(model), input, output);
    }
}
