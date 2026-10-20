package ro.mathlms.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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
import java.util.List;

/**
 * Proposes EN chapters with OpenAI (Responses API, strict JSON-schema output), from the questions' TEXT only - no
 * images, no student data - with {@code store=false}. Same key, model and error wording as {@link OpenAiPageExtractor}.
 */
@Component
public class OpenAiChapterSuggester implements ChapterSuggester {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChapterSuggester.class);

    static final Duration TIMEOUT = Duration.ofSeconds(90);
    static final int MAX_OUTPUT_TOKENS = 8_000;

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
                    ? PageReading.TOO_SLOW : OpenAiPageExtractor.UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
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
        body.put("instructions", ChapterReading.instructions(chapters));
        body.put("store", false);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.putObject("reasoning").put("effort", reasoningEffort);
        }
        ObjectNode message = body.putArray("input").addObject();
        message.put("role", "user");
        message.putArray("content").addObject().put("type", "input_text").put("text", ChapterReading.questionsText(questions));
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "en_chapters");
        format.put("strict", true);
        format.set("schema", ChapterReading.schema(objectMapper));
        return body;
    }

    List<Suggestion> parse(JsonNode response, List<Question> questions, List<Choice> chapters) throws ExtractionException {
        String text = null;
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asString())) {
                continue;
            }
            for (JsonNode part : output.path("content")) {
                if ("refusal".equals(part.path("type").asString())) {
                    throw new ExtractionException(PageReading.REFUSED);
                }
                if ("output_text".equals(part.path("type").asString())) {
                    text = part.path("text").asString();
                }
            }
        }
        if (text == null) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        try {
            return ChapterReading.suggestions(objectMapper, text, questions, chapters);
        } catch (ExtractionException e) {
            log.warn("OpenAI (chapters) answer is not the expected JSON", e.getCause());
            throw e;
        }
    }
}
