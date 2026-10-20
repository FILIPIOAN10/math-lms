package ro.mathlms.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import ro.mathlms.ingest.ChapterSuggester.Choice;
import ro.mathlms.ingest.ChapterSuggester.PartSuggestion;
import ro.mathlms.ingest.ChapterSuggester.Question;
import ro.mathlms.ingest.ChapterSuggester.Suggestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What every chapter suggester asks and expects, whatever the provider: the instructions (with the chapter list), the
 * questions as text, the JSON shape of the answer and how it is read. Provider classes only add transport.
 */
final class ChapterReading {

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

    private ChapterReading() {
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

    static ObjectNode schema(ObjectMapper objectMapper) {
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

    /**
     * One suggestion per question from the answer's JSON text. A missing question, a code we do not have, or a missing
     * requirement is "no proposal"; anything that is not that shape is an {@link ExtractionException}.
     */
    static List<Suggestion> suggestions(ObjectMapper objectMapper, String text, List<Question> questions,
                                        List<Choice> chapters) throws ExtractionException {
        Set<String> known = chapters.stream().map(Choice::code).collect(Collectors.toSet());
        Map<Integer, JsonNode> byIndex = new HashMap<>();
        try {
            for (JsonNode node : objectMapper.readTree(text).path("items")) {
                byIndex.putIfAbsent(node.path("index").asInt(), node);
            }
        } catch (Exception e) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER, e);
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
        String code = node.path("code").isString() ? node.path("code").asString().strip().toUpperCase() : null;
        if (code == null || !known.contains(code)) {
            return new PartSuggestion(null, null);
        }
        String reason = node.path("reason").isString() ? node.path("reason").asString().strip() : null;
        return new PartSuggestion(code, reason == null || reason.isEmpty() ? null : truncate(reason));
    }

    private static String truncate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 299) + "…";
    }
}
