package ro.mathlms.ingest;

import ro.mathlms.ingest.BaremReader.Entry;
import ro.mathlms.ingest.BaremReader.OutlineItem;
import ro.mathlms.ingest.BaremReader.OutlinePart;
import ro.mathlms.ingest.BaremReader.PartBarem;
import ro.mathlms.quiz.QuizItemType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What every barem reader asks and expects, whatever the provider: the instructions, the test's questions written out
 * for the model, the JSON shape of the answer and how it is read. Provider classes only add transport.
 */
final class BaremReading {

    static final String NOT_A_BAREM = "Nu am găsit în poze baremul acestui test. Fotografiază paginile cu baremul, "
            + "drept și cu lumină bună.";

    static final String INSTRUCTIONS = """
            You read the official answer key ("barem de evaluare și de notare") of a Romanian mathematics test from \
            photos, and map it onto the questions of that test as they are stored in a school app. Each image is \
            preceded by a label "Pagina N". After the images comes the list of the test's questions, one per line, \
            numbered as the app shows them ("I.1", "II.3", "III.2", or just "3" in a test without sections), with their \
            type, answer options and requirements.

            Rules:
            - Return one entry per question of the list that the barem covers, with "label" exactly as in the list. \
            Skip questions the barem does not cover; never invent a question.
            - Multiple-choice question ("grilă"): "correctLetter" is the letter of the right answer in the barem, \
            lowercase, without parenthesis ("b"). When the barem gives the right answer by its value instead of a \
            letter, use the letter of the option with that value in the list. Then "barem" is null and "parts" is [].
            - Open question with requirements a), b)... in the list: "correctLetter" is null, "barem" is null, and \
            "parts" has one element per requirement with its "label" ("a", "b") and its barem.
            - Open question without requirements: "correctLetter" is null, "parts" is [], and "barem" is its whole \
            barem.
            - Copy the barem faithfully, in Romanian with diacritics (ă, â, î, ș, ț), one step per line, keeping the \
            points of each step at the end of its line, e.g. "$P = 2(L + l)$ ... 1p". Write mathematics in LaTeX \
            between single dollar signs. Do not add steps, do not solve anything yourself, do not shorten.
            - Leave out the barem's general instructions ("Se punctează orice modalitate de rezolvare corectă...", \
            "Nota finală se calculează...").
            - "warnings": short notes in Romanian for anything uncertain about that question, e.g. "Litera nu se \
            citește clar." or "Baremul dă 3p, testul are 2p."; [] when everything is clear.
            - If the images are not a barem, return "entries": [].""";

    private BaremReading() {
    }

    /** The test's questions, one per line, as the model gets them after the photos. */
    static String outline(List<OutlineItem> questions) {
        StringBuilder text = new StringBuilder("Întrebările testului:\n");
        for (OutlineItem q : questions) {
            text.append(q.label()).append(" | ")
                    .append(q.type() == QuizItemType.SINGLE_CHOICE ? "grilă" : "cu rezolvare").append(" | ")
                    .append(q.points()).append("p | ").append(oneLine(q.statement()));
            if (q.type() == QuizItemType.SINGLE_CHOICE && !q.options().isEmpty()) {
                text.append(" | variante:");
                for (int i = 0; i < q.options().size(); i++) {
                    text.append(' ').append(letter(i)).append(") ").append(oneLine(q.options().get(i)))
                            .append(i + 1 < q.options().size() ? ";" : "");
                }
            }
            if (!q.parts().isEmpty()) {
                text.append(" | cerințe:");
                for (OutlinePart part : q.parts()) {
                    text.append(' ').append(part.label()).append(") (").append(part.points()).append("p) ")
                            .append(oneLine(part.statement())).append(';');
                }
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** "a", "b", "c"... - an option's letter by its place, as on the paper test. */
    static String letter(int index) {
        return String.valueOf((char) ('a' + index));
    }

    private static String oneLine(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }

    /** The answer's JSON Schema: every property required, "may be missing" is a null type (as {@link PageReading}). */
    static ObjectNode schema(ObjectMapper objectMapper) {
        ObjectNode part = objectMapper.createObjectNode();
        part.put("type", "object");
        part.put("additionalProperties", false);
        ObjectNode partProps = part.putObject("properties");
        partProps.putObject("label").put("type", "string");
        partProps.putObject("barem").put("type", "string");
        part.putArray("required").add("label").add("barem");

        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("type", "object");
        entry.put("additionalProperties", false);
        ObjectNode props = entry.putObject("properties");
        props.putObject("label").put("type", "string");
        props.putObject("correctLetter").putArray("type").add("string").add("null");
        props.putObject("barem").putArray("type").add("string").add("null");
        ObjectNode parts = props.putObject("parts");
        parts.put("type", "array");
        parts.set("items", part);
        ObjectNode warnings = props.putObject("warnings");
        warnings.put("type", "array");
        warnings.putObject("items").put("type", "string");
        entry.putArray("required").add("label").add("correctLetter").add("barem").add("parts").add("warnings");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode entries = root.putObject("properties").putObject("entries");
        entries.put("type", "array");
        entries.set("items", entry);
        root.putArray("required").add("entries");
        return root;
    }

    /**
     * The entries in the answer's JSON text, tidied: a letter is one lowercase letter or nothing, blank texts are null.
     * Anything that is not that shape is an {@link ExtractionException}.
     */
    static List<Entry> entries(ObjectMapper objectMapper, String text) throws ExtractionException {
        List<Entry> entries = new ArrayList<>();
        try {
            for (JsonNode node : objectMapper.readTree(text).path("entries")) {
                List<PartBarem> parts = new ArrayList<>();
                for (JsonNode part : node.path("parts")) {
                    String label = blankToNull(part.path("label").asString(""));
                    String barem = blankToNull(part.path("barem").asString(""));
                    if (label != null && barem != null) {
                        parts.add(new PartBarem(label.replaceAll("[).\\s]", "").toLowerCase(Locale.ROOT), barem));
                    }
                }
                List<String> warnings = new ArrayList<>();
                node.path("warnings").forEach(w -> {
                    String warning = blankToNull(w.asString(""));
                    if (warning != null) {
                        warnings.add(warning);
                    }
                });
                String label = blankToNull(node.path("label").asString(""));
                if (label == null) {
                    continue;
                }
                entries.add(new Entry(label, letterOrNull(node.path("correctLetter").asString("")),
                        blankToNull(node.path("barem").asString("")), parts, warnings));
            }
        } catch (Exception e) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER, e);
        }
        return entries;
    }

    /** "B)", " b " -> "b"; anything that is not a single letter -> null. */
    private static String letterOrNull(String raw) {
        String letter = raw == null ? "" : raw.replaceAll("[()\\s.]", "").toLowerCase(Locale.ROOT);
        return letter.matches("[a-z]") ? letter : null;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
