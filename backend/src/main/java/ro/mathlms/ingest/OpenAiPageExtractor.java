package ro.mathlms.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
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

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Reads textbook pages with OpenAI (Responses API, image input, strict JSON-schema output). Only the page images and
 * fixed instructions are sent - never names or student work - and {@code store=false} asks OpenAI not to keep the
 * response. The model copies statements; it is told never to solve or to say which option is right.
 *
 * <p>Settings ({@code app.ai.openai.*}): the key (from {@code OPENAI_API_KEY} in {@code .env}), the model, the
 * reasoning effort and the image detail. Every failure becomes an {@link ExtractionException} worded for the teacher.
 */
@Component
public class OpenAiPageExtractor implements PageExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPageExtractor.class);

    static final String NAME = "openai";
    static final Duration TIMEOUT = Duration.ofSeconds(180);
    /** Reasoning tokens count here too; OpenAI suggests leaving plenty of room, and 5 pages of text fit easily. */
    static final int MAX_OUTPUT_TOKENS = 25_000;

    static final String NO_KEY = "Cheia OpenAI lipsește din configurarea serverului.";
    static final String BAD_KEY = "Cheia OpenAI nu e validă sau nu are acces la model.";
    static final String RATE_LIMITED = "OpenAI refuză momentan cererile (limită atinsă sau credit epuizat). Încearcă mai târziu.";
    static final String UNAVAILABLE = "OpenAI nu răspunde acum. Încearcă din nou peste câteva minute.";
    static final String REJECTED = "OpenAI a respins cererea. Verifică pozele (format, mărime) și încearcă din nou.";


    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final String imageDetail;

    @Autowired
    public OpenAiPageExtractor(RestClient.Builder builder, ObjectMapper objectMapper,
                               @Value("${app.ai.openai.api-key:}") String apiKey,
                               @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                               @Value("${app.ai.openai.model:gpt-6-luna}") String model,
                               @Value("${app.ai.openai.reasoning-effort:low}") String reasoningEffort,
                               @Value("${app.ai.openai.image-detail:high}") String imageDetail) {
        this(builder.clone().baseUrl(baseUrl).requestFactory(timeouts()).build(), objectMapper, apiKey, model,
                reasoningEffort, imageDetail);
    }

    /** For tests: a client bound to a mock server. */
    OpenAiPageExtractor(RestClient restClient, ObjectMapper objectMapper, String apiKey, String model,
                        String reasoningEffort, String imageDetail) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.imageDetail = imageDetail;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public ExtractionResult extract(List<PageImage> pages) throws ExtractionException {
        if (apiKey.isEmpty()) {
            throw new ExtractionException(NO_KEY);
        }
        JsonNode response;
        try {
            response = restClient.post().uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(pages))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // The body explains a 400 (bad image, unknown model); it never contains the key.
            log.warn("OpenAI answered {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExtractionException(messageFor(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            log.warn("OpenAI could not be reached: {}", e.getMessage());
            throw new ExtractionException(e.getCause() instanceof java.net.SocketTimeoutException ? PageReading.TOO_SLOW : UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        return parse(response);
    }

    static String messageFor(HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) {
            return BAD_KEY;
        }
        if (status.value() == 429) {
            return RATE_LIMITED;
        }
        return status.is5xxServerError() ? UNAVAILABLE : REJECTED;
    }

    ObjectNode request(List<PageImage> pages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", PageReading.INSTRUCTIONS);
        body.put("store", false);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.putObject("reasoning").put("effort", reasoningEffort);
        }
        ObjectNode message = body.putArray("input").addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        for (int i = 0; i < pages.size(); i++) {
            PageImage page = pages.get(i);
            content.addObject().put("type", "input_text").put("text", "Pagina " + (i + 1));
            content.addObject().put("type", "input_image").put("detail", imageDetail)
                    .put("image_url", "data:" + page.mediaType() + ";base64,"
                            + Base64.getEncoder().encodeToString(page.bytes()));
        }
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "textbook_exercises");
        format.put("strict", true);
        format.set("schema", PageReading.schema(objectMapper));
        return body;
    }

    ExtractionResult parse(JsonNode response) throws ExtractionException {
        if ("incomplete".equals(response.path("status").asString())) {
            log.warn("OpenAI response incomplete: {}", response.path("incomplete_details"));
            throw new ExtractionException(PageReading.TOO_LONG);
        }
        String text = null;
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asString())) {
                continue; // reasoning items
            }
            for (JsonNode part : output.path("content")) {
                switch (part.path("type").asString()) {
                    case "refusal" -> {
                        log.warn("OpenAI refused: {}", part.path("refusal").asString());
                        throw new ExtractionException(PageReading.REFUSED);
                    }
                    case "output_text" -> text = part.path("text").asString();
                    default -> { }
                }
            }
        }
        if (text == null) {
            log.warn("OpenAI response without text, status {}", response.path("status").asString());
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        List<ExtractedItem> items;
        try {
            items = PageReading.items(objectMapper, text);
        } catch (ExtractionException e) {
            log.warn("OpenAI answer is not the expected JSON", e.getCause());
            throw e;
        }
        JsonNode usage = response.path("usage");
        return new ExtractionResult(items, response.path("model").asString(model),
                usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null);
    }
}
