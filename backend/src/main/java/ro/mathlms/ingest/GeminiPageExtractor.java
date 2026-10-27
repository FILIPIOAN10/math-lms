package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;
import java.util.List;

/**
 * Reads textbook pages with Gemini on Google Cloud: the same instructions and JSON shape as {@link OpenAiPageExtractor}
 * ({@link PageReading}), the photos sent inline, the answer forced to that JSON Schema. Only page images and fixed
 * instructions leave the server - never names or student work. Selected by {@code AI_PROVIDER=gemini}.
 */
@Component
public class GeminiPageExtractor implements PageExtractor {

    private static final Logger log = LoggerFactory.getLogger(GeminiPageExtractor.class);

    static final String NAME = "gemini";
    /** Thinking counts here too; 5 pages of exercises fit easily. */
    static final int MAX_OUTPUT_TOKENS = 25_000;
    static final String THINKING_LEVEL = "LOW";

    private final GeminiClient client;
    private final ObjectMapper objectMapper;

    public GeminiPageExtractor(GeminiClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public ExtractionResult extract(List<PageImage> pages) throws ExtractionException {
        GeminiClient.Reply reply = client.reply(client.generate(request(pages)));
        List<ExtractedItem> items;
        try {
            items = PageReading.items(objectMapper, reply.text());
        } catch (ExtractionException e) {
            log.warn("Gemini answer is not the expected JSON", e.getCause());
            throw e;
        }
        return new ExtractionResult(items, reply.model(), reply.inputTokens(), reply.outputTokens());
    }

    ObjectNode request(List<PageImage> pages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", PageReading.INSTRUCTIONS);
        ObjectNode message = body.putArray("contents").addObject();
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        for (int i = 0; i < pages.size(); i++) {
            PageImage page = pages.get(i);
            parts.addObject().put("text", "Pagina " + (i + 1));
            parts.addObject().putObject("inlineData")
                    .put("mimeType", page.mediaType())
                    .put("data", Base64.getEncoder().encodeToString(page.bytes()));
        }
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", PageReading.schema(objectMapper));
        config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        // Copying printed exercises needs little reasoning, and thinking is billed as output: the model's default
        // (medium on gemini-3.8-flash) cost more than the transcription itself.
        config.putObject("thinkingConfig").put("thinkingLevel", THINKING_LEVEL);
        return body;
    }
}
