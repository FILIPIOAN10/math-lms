package ro.mathlms.aigrading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.ingest.ExtractionException;
import ro.mathlms.ingest.GeminiClient;
import ro.mathlms.ingest.PageExtractor.PageImage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Base64;

/**
 * Proposes grades with Gemini on Google Cloud, with the strongest model ({@code app.ai.gemini.strong-model}): reading
 * handwriting against a barem is the hardest thing the app asks of an AI. The problem and its barem go first, then the
 * figure and the student's pages inline; the answer is forced to {@link GradingReading}'s JSON Schema.
 */
@Component
public class GeminiAnswerGrader implements AnswerGrader {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnswerGrader.class);

    static final String NAME = "gemini";
    /** Thinking counts here too: room to read every page and check every barem step. */
    static final int MAX_OUTPUT_TOKENS = 32_000;

    private final GeminiClient client;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String thinkingLevel;

    public GeminiAnswerGrader(GeminiClient client, ObjectMapper objectMapper,
                              @Value("${app.ai.gemini.strong-model:gemini-4-argon}") String model,
                              @Value("${app.ai.gemini.grading-thinking-level:MEDIUM}") String thinkingLevel) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
        this.thinkingLevel = thinkingLevel;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public Proposal grade(Task task) throws ExtractionException {
        GeminiClient.Reply reply = client.reply(client.generate(request(task), model));
        try {
            return GradingReading.proposal(objectMapper, reply.text(), task, reply.model(), reply.inputTokens(),
                    reply.outputTokens());
        } catch (ExtractionException e) {
            log.warn("Gemini grading answer not usable: {}", e.getMessage());
            throw e;
        }
    }

    ObjectNode request(Task task) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", GradingReading.INSTRUCTIONS);
        ObjectNode message = body.putArray("contents").addObject();
        message.put("role", "user");
        ArrayNode parts = message.putArray("parts");
        parts.addObject().put("text", GradingReading.problem(task));
        if (task.figure() != null) {
            parts.addObject().put("text", "Figura problemei:");
            inline(parts, task.figure());
        }
        parts.addObject().put("text", "Rezolvarea elevului:");
        for (int i = 0; i < task.photos().size(); i++) {
            parts.addObject().put("text", "Pagina " + (i + 1));
            inline(parts, task.photos().get(i));
        }
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", GradingReading.schema(objectMapper));
        config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        GeminiClient.thinkingLevel(config, thinkingLevel);
        return body;
    }

    private static void inline(ArrayNode parts, PageImage image) {
        parts.addObject().putObject("inlineData")
                .put("mimeType", image.mediaType())
                .put("data", Base64.getEncoder().encodeToString(image.bytes()));
    }
}
