package ro.mathlms.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import ro.mathlms.quiz.QuizItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * What every page reader asks and expects, whatever the provider: the instructions, the JSON shape of the answer, how
 * that answer is read, and the provider-neutral failures worded for the teacher. Provider classes only add transport.
 */
final class PageReading {

    static final String TOO_SLOW = "Citirea a durat prea mult. Încearcă din nou, eventual cu mai puține pagini.";
    static final String TOO_LONG = "Paginile au prea mult text pentru o singură citire. Trimite mai puține pagini odată.";
    static final String REFUSED = "AI-ul a refuzat să citească pozele. Verifică dacă sunt pagini de manual.";
    static final String UNREADABLE_ANSWER = "Răspunsul AI-ului nu a putut fi înțeles. Încearcă din nou.";

    static final String INSTRUCTIONS = """
            You transcribe mathematics exercises from photographed pages of a Romanian school textbook or exercise book, so \
            a teacher can turn them into a test. Each page image is preceded by a label "Pagina N".

            Rules:
            - Transcribe every exercise (problem) on the pages, in reading order. Skip theory, definitions, worked examples \
            whose solution is printed, page headers/footers and page numbers.
            - Copy the statement exactly, in Romanian with diacritics (ă, â, î, ș, ț), WITHOUT the exercise number ("1.", \
            "3)") and without the printed points ("5p"). Do NOT solve anything, do NOT add hints, and NEVER say which \
            option is correct.
            - Write all mathematics in LaTeX between single dollar signs, e.g. $\\frac{2}{3}$, $x^2 - 4 = 0$, $\\sqrt{5}$.
            - type = "SINGLE_CHOICE" when the exercise lists answer choices (a, b, c, d / A, B, C, D); put the choices in \
            "options" in order, WITHOUT their letters. Otherwise type = "OPEN" and "options" is [].
            - An exercise with sub-points a), b), c) that are separate tasks (not answer choices) stays ONE open item: \
            "statement" is only the shared text above the sub-points, and each sub-point goes in "parts" in order, with \
            its own text (without the letter) and the points printed next to it (e.g. "(2p) a) Arată că $x=25$." -> \
            {"statement": "Arată că $x=25$.", "points": 2}). The item's "points" is then their sum. Without \
            sub-points, "parts" is [].
            - "section": the heading the exercise sits under (e.g. "SUBIECTUL I", "Exerciții de consolidare"), followed by \
            its instruction line, if one is printed, on a new line (e.g. "SUBIECTUL I\\nÎncercuiește litera \
            corespunzătoare răspunsului corect."); null when there is no heading.
            - "points": the points printed next to the exercise (e.g. "5p" -> 5), or the sum of its sub-points' points, or \
            null when none are printed.
            - An exercise may continue on the next page (e.g. sub-point b) at the top of the following photo): it is still \
            ONE item, on the page where it starts.
            - "hasFigure": true when the exercise needs a drawing, graph or table that is shown only as a picture.
            - "figurePage" and "figureBox": when "hasFigure" is true, the N of the "Pagina N" image that shows that \
            drawing and its tight bounding box on that image, "figureBox" = [ymin, xmin, ymax, xmax] normalized to \
            0-1000. Box the drawing (or the table) only - not the statement text, not the answer choices, not the \
            squared writing area. Otherwise "figurePage" is null and "figureBox" is [].
            - "page": the N of the "Pagina N" label of the image the exercise is on.
            - "warnings": short notes in Romanian for anything you could not read with certainty (e.g. "Un cuvânt \
            ilizibil în enunț."); [] when everything is clear.
            - If the images contain no exercises, return "items": [].""";

    private PageReading() {
    }

    /**
     * The answer's JSON Schema: every property required, no extra ones; "may be missing" is a null type. OpenAI's
     * strict mode and Gemini's {@code responseJsonSchema} both accept it as is.
     */
    static ObjectNode schema(ObjectMapper objectMapper) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "object");
        item.put("additionalProperties", false);
        ObjectNode props = item.putObject("properties");
        props.putObject("page").put("type", "integer");
        props.putObject("section").putArray("type").add("string").add("null");
        ObjectNode type = props.putObject("type");
        type.put("type", "string");
        type.putArray("enum").add(QuizItemType.SINGLE_CHOICE.name()).add(QuizItemType.OPEN.name());
        props.putObject("statement").put("type", "string");
        ObjectNode options = props.putObject("options");
        options.put("type", "array");
        options.putObject("items").put("type", "string");
        props.putObject("points").putArray("type").add("integer").add("null");
        props.putObject("hasFigure").put("type", "boolean");
        ObjectNode warnings = props.putObject("warnings");
        warnings.put("type", "array");
        warnings.putObject("items").put("type", "string");
        ObjectNode part = objectMapper.createObjectNode();
        part.put("type", "object");
        part.put("additionalProperties", false);
        ObjectNode partProps = part.putObject("properties");
        partProps.putObject("statement").put("type", "string");
        partProps.putObject("points").putArray("type").add("integer").add("null");
        part.putArray("required").add("statement").add("points");
        props.putObject("figurePage").putArray("type").add("integer").add("null");
        ObjectNode figureBox = props.putObject("figureBox");
        figureBox.put("type", "array");
        figureBox.putObject("items").put("type", "integer");
        ObjectNode parts = props.putObject("parts");
        parts.put("type", "array");
        parts.set("items", part);
        item.putArray("required").add("page").add("section").add("type").add("statement").add("options")
                .add("points").add("hasFigure").add("warnings").add("parts").add("figurePage").add("figureBox");

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        ObjectNode items = root.putObject("properties").putObject("items");
        items.put("type", "array");
        items.set("items", item);
        root.putArray("required").add("items");
        return root;
    }

    /** The questions in the answer's JSON text; anything that is not that shape is an {@link ExtractionException}. */
    static List<ExtractedItem> items(ObjectMapper objectMapper, String text) throws ExtractionException {
        List<ExtractedItem> items = new ArrayList<>();
        try {
            for (JsonNode node : objectMapper.readTree(text).path("items")) {
                items.add(objectMapper.treeToValue(node, ExtractedItem.class));
            }
        } catch (Exception e) {
            throw new ExtractionException(UNREADABLE_ANSWER, e);
        }
        return items;
    }
}
