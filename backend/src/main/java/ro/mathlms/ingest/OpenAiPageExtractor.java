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
import ro.mathlms.quiz.QuizItemType;

import java.time.Duration;
import java.util.ArrayList;
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
    static final String TOO_SLOW = "Citirea a durat prea mult. Încearcă din nou, eventual cu mai puține pagini.";
    static final String REJECTED = "OpenAI a respins cererea. Verifică pozele (format, mărime) și încearcă din nou.";
    static final String TOO_LONG = "Paginile au prea mult text pentru o singură citire. Trimite mai puține pagini odată.";
    static final String REFUSED = "AI-ul a refuzat să citească pozele. Verifică dacă sunt pagini de manual.";
    static final String UNREADABLE_ANSWER = "Răspunsul AI-ului nu a putut fi înțeles. Încearcă din nou.";

    static final String INSTRUCTIONS = """
            You transcribe mathematics exercises from photographed pages of a Romanian school textbook or exercise book, so \
            a teacher can turn them into a test. Each page image is preceded by a label "Pagina N".

            Rules:
            - Transcribe every exercise (problem) on the pages, in reading order. Skip theory, definitions, worked examples \
            whose solution is printed, page headers/footers and page numbers.
            - Copy the statement exactly, in Romanian with diacritics (ă, â, î, ș, ț), WITHOUT the exercise number ("1.", \
            "3)") and without the printed points ("5p"). Do NOT solve anything, do NOT add hints, and NEVER say which \
            option is correct.
            - Write all mathematics in LaTeX between single dollar signs, e.g. $\\frac{2}{3}$, $x^2 - 4 = 0$, $\\sqrt{5}$.
            - type = "SINGLE_CHOICE" when the exercise lists answer choices (a, b, c, d / A, B, C, D); put the choices in \
            "options" in order, WITHOUT their letters. Otherwise type = "OPEN" and "options" is [].
            - An exercise with sub-points a), b), c) that are separate tasks (not answer choices) stays ONE open item: \
            "statement" is only the shared text above the sub-points, and each sub-point goes in "parts" in order, with \
            its own text (without the letter) and the points printed next to it (e.g. "(2p) a) Arată că $x=25$." -> \
            {"statement": "Arată că $x=25$.", "points": 2}). The item's "points" is then their sum. Without \
            sub-points, "parts" is [].
            - "section": the heading the exercise sits under (e.g. "SUBIECTUL I", "Exerciții de consolidare"), followed by \
            its instruction line, if one is printed, on a new line (e.g. "SUBIECTUL I\\nÎncercuiește litera \
            corespunzătoare răspunsului corect."); null when there is no heading.
            - "points": the points printed next to the exercise (e.g. "5p" -> 5), or the sum of its sub-points' points, or \
            null when none are printed.
            - An exercise may continue on the next page (e.g. sub-point b) at the top of the following photo): it is still \
            ONE item, on the page where it starts.
            - "hasFigure": true when the exercise needs a drawing, graph or table that is shown only as a picture.
            - "page": the N of the "Pagina N" label of the image the exercise is on.
            - "warnings": short notes in Romanian for anything you could not read with certainty (e.g. "Un cuvânt \
            ilizibil în enunț."); [] when everything is clear.
            - If the images contain no exercises, return "items": [].""";

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
            throw new ExtractionException(e.getCause() instanceof java.net.SocketTimeoutException ? TOO_SLOW : UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(UNREADABLE_ANSWER);
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
        body.put("instructions", INSTRUCTIONS);
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
        format.set("schema", schema());
        return body;
    }

    /** Strict mode: every property required, no extra ones; "may be missing" is a null type. */
    private ObjectNode schema() {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "object");
        item.put("additionalProperties", false);
        ObjectNode props = item.putObject("properties");
        props.putObject("page").put("type", "integer");
        props.putObject("section").putArray("type").add("string").add("null");
        ObjectNode type = props.putObject("type");
        type.put("type", "string");
        type.putArray("enum").add(QuizItemType.SINGLE_CHOICE.name()).add(QuizItemType.OPEN.name());
        props.putObject("statement").put("type", "string");
        ObjectNode options = props.putObject("options");
        options.put("type", "array");
        options.putObject("items").put("type", "string");
        props.putObject("points").putArray("type").add("integer").add("null");
        props.putObject("hasFigure").put("type", "boolean");
        ObjectNode warnings = props.putObject("warnings");
        warnings.put("type", "array");
        warnings.putObject("items").put("type", "string");
        ObjectNode part = objectMapper.createObjectNode();
        part.put("type", "object");
        part.put("additionalProperties", false);
        ObjectNode partProps = part.putObject("properties");
        partProps.putObject("statement").put("type", "string");
        partProps.putObject("points").putArray("type").add("integer").add("null");
        part.putArray("required").add("statement").add("points");
        ObjectNode parts = props.putObject("parts");
        parts.put("type", "array");
        parts.set("items", part);
        item.putArray("required").add("page").add("section").add("type").add("statement").add("options")
                .add("points").add("hasFigure").add("warnings").add("parts");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode items = root.putObject("properties").putObject("items");
        items.put("type", "array");
        items.set("items", item);
        root.putArray("required").add("items");
        return root;
    }

    ExtractionResult parse(JsonNode response) throws ExtractionException {
        if ("incomplete".equals(response.path("status").asString())) {
            log.warn("OpenAI response incomplete: {}", response.path("incomplete_details"));
            throw new ExtractionException(TOO_LONG);
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
                        throw new ExtractionException(REFUSED);
                    }
                    case "output_text" -> text = part.path("text").asString();
                    default -> { }
                }
            }
        }
        if (text == null) {
            log.warn("OpenAI response without text, status {}", response.path("status").asString());
            throw new ExtractionException(UNREADABLE_ANSWER);
        }
        List<ExtractedItem> items = new ArrayList<>();
        try {
            for (JsonNode node : objectMapper.readTree(text).path("items")) {
                items.add(objectMapper.treeToValue(node, ExtractedItem.class));
            }
        } catch (Exception e) {
            log.warn("OpenAI answer is not the expected JSON", e);
            throw new ExtractionException(UNREADABLE_ANSWER, e);
        }
        JsonNode usage = response.path("usage");
        return new ExtractionResult(items, response.path("model").asString(model),
                usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null,
                usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null);
    }
}
