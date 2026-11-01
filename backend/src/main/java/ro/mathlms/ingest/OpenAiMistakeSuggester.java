package ro.mathlms.ingest;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;

/** Proposes the mistake behind a wrong option with OpenAI. Selected by {@code AI_PROVIDER=openai}. */
@Component
public class OpenAiMistakeSuggester implements MistakeSuggester {

    private static final Logger log = LoggerFactory.getLogger(OpenAiMistakeSuggester.class);

    static final Duration TIMEOUT = Duration.ofSeconds(60);
    static final int MAX_OUTPUT_TOKENS = 4_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    @Autowired
    public OpenAiMistakeSuggester(RestClient.Builder builder, ObjectMapper objectMapper,
                                  @Value("${app.ai.openai.api-key:}") String apiKey,
                                  @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                  @Value("${app.ai.openai.model:gpt-6-luna}") String model) {
        this(builder.clone().baseUrl(baseUrl).requestFactory(timeouts()).build(), objectMapper, apiKey, model);
    }

    /** For tests: a client bound to a mock server. */
    OpenAiMistakeSuggester(RestClient restClient, ObjectMapper objectMapper, String apiKey, String model) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
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
    public String suggest(Question question) throws ExtractionException {
        if (apiKey.isEmpty()) {
            throw new ExtractionException(OpenAiPageExtractor.NO_KEY);
        }
        JsonNode response;
        try {
            response = restClient.post().uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(question))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("OpenAI (mistake) answered {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExtractionException(OpenAiPageExtractor.messageFor(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            log.warn("OpenAI (mistake) could not be reached: {}", e.getMessage());
            throw new ExtractionException(e.getCause() instanceof java.net.SocketTimeoutException
                    ? PageReading.TOO_SLOW : OpenAiPageExtractor.UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        return MistakeReading.cleaned(text(response));
    }

    ObjectNode request(Question question) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", MistakeReading.INSTRUCTIONS);
        body.put("store", false);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);
        ObjectNode message = body.putArray("input").addObject();
        message.put("role", "user");
        message.putArray("content").addObject().put("type", "input_text").put("text", MistakeReading.questionText(question));
        return body;
    }

    private static String text(JsonNode response) throws ExtractionException {
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asString())) {
                continue;
            }
            for (JsonNode part : output.path("content")) {
                if ("refusal".equals(part.path("type").asString())) {
                    throw new ExtractionException(PageReading.REFUSED);
                }
                if ("output_text".equals(part.path("type").asString())) {
                    return part.path("text").asString();
                }
            }
        }
        return null;
    }
}
