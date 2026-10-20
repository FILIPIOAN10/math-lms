package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Proposes EN chapters with Gemini on Google Cloud, from the questions' TEXT only - no images, no student data - with
 * the same prompt and reading as {@link OpenAiChapterSuggester} ({@link ChapterReading}). Selected by
 * {@code AI_PROVIDER=gemini}, like the page reader.
 */
@Component
public class GeminiChapterSuggester implements ChapterSuggester {

    private static final Logger log = LoggerFactory.getLogger(GeminiChapterSuggester.class);

    static final int MAX_OUTPUT_TOKENS = 8_000;

    private final GeminiClient client;
    private final ObjectMapper objectMapper;

    public GeminiChapterSuggester(GeminiClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return GeminiPageExtractor.NAME;
    }

    @Override
    public List<Suggestion> suggest(List<Question> questions, List<Choice> chapters) throws ExtractionException {
        GeminiClient.Reply reply = client.reply(client.generate(request(questions, chapters)));
        // What each call costs, in the log next to the import's own (an import keeps its tokens on its row).
        log.info("Gemini (chapters): {} questions, {} input + {} output tokens, model {}", questions.size(),
                reply.inputTokens(), reply.outputTokens(), reply.model());
        try {
            return ChapterReading.suggestions(objectMapper, reply.text(), questions, chapters);
        } catch (ExtractionException e) {
            log.warn("Gemini (chapters) answer is not the expected JSON", e.getCause());
            throw e;
        }
    }

    ObjectNode request(List<Question> questions, List<Choice> chapters) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject()
                .put("text", ChapterReading.instructions(chapters));
        ObjectNode message = body.putArray("contents").addObject();
        message.put("role", "user");
        message.putArray("parts").addObject().put("text", ChapterReading.questionsText(questions));
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", ChapterReading.schema(objectMapper));
        config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        return body;
    }
}
