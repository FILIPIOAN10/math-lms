package ro.mathlms.ingest;

import ro.mathlms.aiusage.AiUsageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Proposes the mistake behind a wrong option with Gemini on Google Cloud. Selected by {@code AI_PROVIDER=gemini}. */
@Component
public class GeminiMistakeSuggester implements MistakeSuggester {

    private static final Logger log = LoggerFactory.getLogger(GeminiMistakeSuggester.class);

    static final int MAX_OUTPUT_TOKENS = 4_000;

    private final GeminiClient client;
    private final ObjectMapper objectMapper;

    public GeminiMistakeSuggester(GeminiClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return GeminiPageExtractor.NAME;
    }

    @Override
    public String suggest(Question question) throws ExtractionException {
        GeminiClient.Reply reply = client.reply(client.generate(request(question)), AiUsageRecorder.MISTAKES);
        log.info("Gemini (mistake): {} input + {} output tokens, model {}", reply.inputTokens(), reply.outputTokens(),
                reply.model());
        return MistakeReading.cleaned(reply.text());
    }

    ObjectNode request(Question question) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", MistakeReading.INSTRUCTIONS);
        ObjectNode message = body.putArray("contents").addObject();
        message.put("role", "user");
        message.putArray("parts").addObject().put("text", MistakeReading.questionText(question));
        body.putObject("generationConfig").put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        return body;
    }
}
