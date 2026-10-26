package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.ingest.PageExtractor.PageImage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;
import java.util.List;

/**
 * Reads a barem with Gemini on Google Cloud, with the strongest model ({@code app.ai.gemini.strong-model}): a wrong
 * letter in the barem would mark every student wrong. The photos go inline, then the test's questions as text, and the
 * answer is forced to {@link BaremReading}'s JSON Schema. Selected by {@code AI_GRADING_PROVIDER=gemini}.
 */
@Component
public class GeminiBaremReader implements BaremReader {

    private static final Logger log = LoggerFactory.getLogger(GeminiBaremReader.class);

    static final String NAME = "gemini";
    /** Thinking counts here too; a barem of 18 questions fits easily. */
    static final int MAX_OUTPUT_TOKENS = 32_000;

    private final GeminiClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    public GeminiBaremReader(GeminiClient client, ObjectMapper objectMapper,
                             @Value("${app.ai.gemini.strong-model:gemini-4-argon}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public Result read(List<PageImage> pages, List<OutlineItem> questions) throws ExtractionException {
        GeminiClient.Reply reply = client.reply(client.generate(request(pages, questions), model));
        List<Entry> entries;
        try {
            entries = BaremReading.entries(objectMapper, reply.text());
        } catch (ExtractionException e) {
            log.warn("Gemini barem answer is not the expected JSON", e.getCause());
            throw e;
        }
        return new Result(entries, reply.model(), reply.inputTokens(), reply.outputTokens());
    }

    ObjectNode request(List<PageImage> pages, List<OutlineItem> questions) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", BaremReading.INSTRUCTIONS);
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
        parts.addObject().put("text", BaremReading.outline(questions));
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", BaremReading.schema(objectMapper));
        config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        return body;
    }
}
