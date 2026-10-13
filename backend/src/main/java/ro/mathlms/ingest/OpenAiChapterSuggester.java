package ro.mathlms.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proposes EN chapters with OpenAI (Responses API, strict JSON-schema output), from the questions' TEXT only - no
 * images, no student data - with {@code store=false}. Same key, model and error wording as {@link OpenAiPageExtractor}.
 */
@Component
public class OpenAiChapterSuggester implements ChapterSuggester {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChapterSuggester.class);

    static final Duration TIMEOUT = Duration.ofSeconds(90);
    static final int MAX_OUTPUT_TOKENS = 8_000;

    static final String INSTRUCTIONS_HEAD = """
            You help a Romanian mathematics teacher preparing students for the Evaluarea Națională (grade 8). For each \
            numbered question, choose the ONE chapter below that the question mainly tests - the theory a student who \
            got it wrong should revise. Judge by what is asked, not by the question's number. When a question has \
            requirements a), b), c), choose a chapter for each requirement separately (they often test different things).

            Rules:
            - "code" must be one of the codes listed below, or null when none fits.
            - "reason": one short sentence in Romanian naming the clue in the statement (e.g. "Apare $(x+3)^2$: formule \
            de calcul prescurtat."); null when "code" is null.
            - "index" is the question's number; "parts" has one entry per requirement, in order ([] when it has none).
            - Never solve the question and never say which answer is right.

            Chapters (code — title: how to recognise it):
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;

    @Autowired
    public OpenAiChapterSuggester(RestClient.Builder builder, ObjectMapper objectMapper,
                                  @Value("${app.ai.openai.api-key:}") String apiKey,
                                  @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                  @Value("${app.ai.openai.model:gpt-6-luna}") String model,
                                  @Value("${app.ai.openai.reasoning-effort:low}") String reasoningEffort) {
        this(builder.clone().baseUrl(baseUrl).requestFactory(timeouts()).build(), objectMapper, apiKey, model,
                reasoningEffort);
    }

    /** For tests: a client bound to a mock server. */
    OpenAiChapterSuggester(RestClient restClient, ObjectMapper objectMapper, String apiKey, String model,
                           String reasoningEffort) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
        this.reasoningEffort = reasoningEffort;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    @Override
    public String provider() {
        return OpenAiPageExtractor.NAME;
    }

    @Override
    public List<Suggestion> suggest(List<Question> questions, List<Choice> chapters) throws ExtractionException {
        if (apiKey.isEmpty()) {
            throw new ExtractionException(OpenAiPageExtractor.NO_KEY);
        }
        JsonNode response;
        try {
            response = restClient.post().uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(questions, chapters))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("OpenAI (chapters) answered {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExtractionException(OpenAiPageExtractor.messageFor(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            log.warn("OpenAI (chapters) could not be reached: {}", e.getMessage());
            throw new ExtractionException(e.getCause() instanceof java.net.SocketTimeoutException
                    ? OpenAiPageExtractor.TOO_SLOW : OpenAiPageExtractor.UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(OpenAiPageExtractor.UNREADABLE_ANSWER);
        }
        // What each call costs, in the log next to the import's own (an import keeps its tokens on its row).
        JsonNode usage = response.path("usage");
        log.info("OpenAI (chapters): {} questions, {} input + {} output tokens, model {}", questions.size(),
                usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong(), model);
        return parse(response, questions, chapters);
    }

    ObjectNode request(List<Question> questions, List<Choice> chapters) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", instructions(chapters));
        body.put("store", false);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.putObject("reasoning").put("effort", reasoningEffort);
        }
        ObjectNode message = body.putArray("input").addObject();
        message.put("role", "user");
        message.putArray("content").addObject().put("type", "input_text").put("text", questionsText(questions));
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "en_chapters");
        format.put("strict", true);
        format.set("schema", schema());
        return body;
    }

    static String instructions(List<Choice> chapters) {
        StringBuilder text = new StringBuilder(INSTRUCTIONS_HEAD);
        for (Choice c : chapters) {
            text.append("- ").append(c.code()).append(" — ").append(c.title());
            if (c.clues() != null && !c.clues().isBlank()) {
                text.append(": ").append(c.clues());
            }
            text.append('\n');
        }
        return text.toString();
    }

    static String questionsText(List<Question> questions) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            text.append("Întrebarea ").append(i + 1);
            if (q.section() != null && !q.section().isBlank()) {
                text.append(" (").append(q.section().split("\n", 2)[0].strip()).append(')');
            }
            text.append("\nEnunț: ").append(q.statement()).append('\n');
            if (!q.options().isEmpty()) {
                text.append("Variante: ").append(String.join(" | ", q.options())).append('\n');
            }
            for (int p = 0; p < q.parts().size(); p++) {
                text.append((char) ('a' + p)).append(") ").append(q.parts().get(p)).append('\n');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private ObjectNode schema() {
        ObjectNode part = objectMapper.createObjectNode();
        part.put("type", "object");
        part.put("additionalProperties", false);
        ObjectNode partProps = part.putObject("properties");
        partProps.putObject("code").putArray("type").add("string").add("null");
        partProps.putObject("reason").putArray("type").add("string").add("null");
        part.putArray("required").add("code").add("reason");

        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "object");
        item.put("additionalProperties", false);
        ObjectNode props = item.putObject("properties");
        props.putObject("index").put("type", "integer");
        props.putObject("code").putArray("type").add("string").add("null");
        props.putObject("reason").putArray("type").add("string").add("null");
        ObjectNode parts = props.putObject("parts");
        parts.put("type", "array");
        parts.set("items", part);
        item.putArray("required").add("index").add("code").add("reason").add("parts");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode items = root.putObject("properties").putObject("items");
        items.put("type", "array");
        items.set("items", item);
        root.putArray("required").add("items");
        return root;
    }

    List<Suggestion> parse(JsonNode response, List<Question> questions, List<Choice> chapters) throws ExtractionException {
        String text = null;
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode part : output.path("content")) {
                if ("refusal".equals(part.path("type").asText())) {
                    throw new ExtractionException(OpenAiPageExtractor.REFUSED);
                }
                if ("output_text".equals(part.path("type").asText())) {
                    text = part.path("text").asText();
                }
            }
        }
        if (text == null) {
            throw new ExtractionException(OpenAiPageExtractor.UNREADABLE_ANSWER);
        }
        Set<String> known = chapters.stream().map(Choice::code).collect(Collectors.toSet());
        Map<Integer, JsonNode> byIndex = new HashMap<>();
        try {
            for (JsonNode node : objectMapper.readTree(text).path("items")) {
                byIndex.putIfAbsent(node.path("index").asInt(), node);
            }
        } catch (Exception e) {
            log.warn("OpenAI (chapters) answer is not the expected JSON", e);
            throw new ExtractionException(OpenAiPageExtractor.UNREADABLE_ANSWER, e);
        }
        List<Suggestion> out = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            int partCount = questions.get(i).parts().size();
            JsonNode node = byIndex.get(i + 1);
            if (node == null) {
                out.add(Suggestion.none(partCount));
                continue;
            }
            PartSuggestion whole = read(node, known);
            List<PartSuggestion> parts = new ArrayList<>();
            JsonNode given = node.path("parts");
            for (int p = 0; p < partCount; p++) {
                parts.add(p < given.size() ? read(given.get(p), known) : new PartSuggestion(null, null));
            }
            out.add(new Suggestion(whole.code(), whole.reason(), parts));
        }
        return out;
    }

    /** A code we do not have is no suggestion at all (and its reason goes with it). */
    private static PartSuggestion read(JsonNode node, Set<String> known) {
        String code = node.path("code").isTextual() ? node.path("code").asText().strip().toUpperCase() : null;
        if (code == null || !known.contains(code)) {
            return new PartSuggestion(null, null);
        }
        String reason = node.path("reason").isTextual() ? node.path("reason").asText().strip() : null;
        return new PartSuggestion(code, reason == null || reason.isEmpty() ? null : truncate(reason));
    }

    private static String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 299) + "…";
    }
}
