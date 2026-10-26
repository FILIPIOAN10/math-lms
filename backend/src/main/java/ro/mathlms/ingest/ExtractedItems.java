package ro.mathlms.ingest;

import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tidies what a provider returned before the teacher sees it: trims, drops empty questions, caps sizes, and warns
 * (never fixes silently) where the reading looks wrong - the teacher decides in the review.
 */
final class ExtractedItems {

    /** More than this from 5 pages is a misreading, not a test. */
    static final int MAX_ITEMS = 60;
    static final int MAX_STATEMENT = 4000;
    static final int MAX_OPTION = 500;
    static final int MAX_OPTIONS = 6;
    static final int MAX_POINTS = 100;

    static final String UNBALANCED_MATH = "Formulă incompletă (un $ fără pereche) - verifică enunțul.";
    static final String TOO_FEW_OPTIONS = "Grilă cu mai puțin de 2 variante - completează variantele sau fă-o întrebare cu rezolvare.";
    static final String FIGURE = "Are un desen: adaugă-l ca imagine după ce creezi testul.";
    static final String FIGURE_CROPPED = "Am decupat desenul din poză: verifică decupajul (îl poți ajusta sau scoate).";

    /** Added around the AI's box on the 0-1000 scale (about 0.8 %): its boxes tend to touch the drawing's edges. */
    static final int FIGURE_MARGIN = 8;
    /** A box thinner than this (1.5 % of the page) is a misreading, not a drawing. */
    static final int MIN_FIGURE = 15;

    private ExtractedItems() {
    }

    static List<ExtractedItem> normalize(List<ExtractedItem> raw, int pageCount) {
        List<ExtractedItem> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (ExtractedItem item : raw) {
            if (item == null || out.size() == MAX_ITEMS) {
                continue;
            }
            String statement = cap(trim(withoutNumber(item.statement())), MAX_STATEMENT);
            if (statement == null) {
                continue;
            }
            QuizItemType type = item.type() == null ? QuizItemType.OPEN : item.type();
            List<String> options = new ArrayList<>();
            if (type == QuizItemType.SINGLE_CHOICE && item.options() != null) {
                for (String option : item.options()) {
                    String text = cap(trim(option), MAX_OPTION);
                    if (text != null && options.size() < MAX_OPTIONS) {
                        options.add(text);
                    }
                }
            }
            List<String> warnings = new ArrayList<>();
            if (item.warnings() != null) {
                item.warnings().stream().map(ExtractedItems::trim).filter(Objects::nonNull)
                        .map(w -> cap(w, 300)).limit(5).forEach(warnings::add);
            }
            if (!balancedMath(statement) || options.stream().anyMatch(o -> !balancedMath(o))) {
                warnings.add(UNBALANCED_MATH);
            }
            if (type == QuizItemType.SINGLE_CHOICE && options.size() < 2) {
                warnings.add(TOO_FEW_OPTIONS);
            }
            int page = Math.clamp(item.page(), 1, Math.max(1, pageCount));
            Integer figurePage = item.figurePage() == null ? page : item.figurePage();
            List<Integer> figureBox = item.hasFigure() ? figureBox(item.figureBox(), figurePage, pageCount) : List.of();
            if (figureBox.isEmpty()) {
                figurePage = null;
            }
            if (item.hasFigure()) {
                warnings.add(figureBox.isEmpty() ? FIGURE : FIGURE_CROPPED);
            }
            List<ExtractedItem.Part> parts = new ArrayList<>();
            if (type == QuizItemType.OPEN && item.parts() != null) {
                for (ExtractedItem.Part part : item.parts()) {
                    String text = part == null ? null : cap(trim(withoutPartLabel(part.statement())), MAX_STATEMENT);
                    if (text != null && parts.size() < QuizItemPart.MAX_PARTS) {
                        Integer partPoints = part.points() == null || part.points() < 0 || part.points() > MAX_POINTS
                                ? null : part.points();
                        parts.add(new ExtractedItem.Part(text, partPoints));
                    }
                }
                if (parts.stream().anyMatch(p -> !balancedMath(p.statement()))) {
                    warnings.add(UNBALANCED_MATH);
                }
            }
            Integer points = item.points() == null || item.points() < 0 || item.points() > MAX_POINTS ? null : item.points();
            if (!parts.isEmpty() && parts.stream().allMatch(p -> p.points() != null)) {
                points = parts.stream().mapToInt(ExtractedItem.Part::points).sum(); // the printed parts are what counts
            }
            out.add(new ExtractedItem(page, cap(enHeading(trim(item.section())), QuizItem.MAX_SECTION), type, statement,
                    List.copyOf(options), points, item.hasFigure(), List.copyOf(warnings), List.copyOf(parts), null, null,
                    figurePage, figureBox));
        }
        return inExamOrder(out);
    }

    private static final java.util.regex.Pattern SUBIECTUL =
            java.util.regex.Pattern.compile("^subiectul\\s+(?:al\\s+)?([ivx]+)\\b",java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Photos sent out of order (Subiectul II's page first) still give SUBIECTUL I, then II, then III. Pages, not
     * questions, are put in order - by the first SUBIECTUL heading on each (a page without one stays after the page
     * before it) - so a question continuing a subject at the top of a page stays with that subject. A question under
     * no heading takes the heading of the one before it, so the numbering runs on. A test without SUBIECTUL headings
     * keeps the order it was read in.
     */
    static List<ExtractedItem> inExamOrder(List<ExtractedItem> items) {
        java.util.Map<Integer, Integer> firstSubject = new java.util.HashMap<>();
        for (ExtractedItem item : items) {
            Integer subject = subjectNumber(item.section());
            if (subject != null) {
                firstSubject.putIfAbsent(item.page(), subject);
            }
        }
        if (firstSubject.isEmpty()) {
            return items;
        }
        java.util.Map<Integer, Integer> pageRank = new java.util.HashMap<>();
        int previous = 0;
        for (ExtractedItem item : items) {
            if (!pageRank.containsKey(item.page())) {
                previous = firstSubject.getOrDefault(item.page(), previous);
                pageRank.put(item.page(), previous);
            }
        }
        List<ExtractedItem> sorted = new ArrayList<>(items);
        sorted.sort(java.util.Comparator.comparingInt(i -> pageRank.get(i.page()))); // stable: reading order within a page
        List<ExtractedItem> out = new ArrayList<>();
        String section = null;
        for (ExtractedItem item : sorted) {
            if (item.section() != null) {
                section = item.section();
            }
            out.add(item.section() != null || section == null ? item : new ExtractedItem(item.page(), section, item.type(),
                    item.statement(), item.options(), item.points(), item.hasFigure(), item.warnings(), item.parts(),
                    item.enChapter(), item.enChapterReason(), item.figurePage(), item.figureBox()));
        }
        return out;
    }

    /** "SUBIECTUL al II-lea..." -> 2; anything else -> null. */
    static Integer subjectNumber(String section) {
        if (section == null) {
            return null;
        }
        java.util.regex.Matcher m = SUBIECTUL.matcher(section.strip());
        if (!m.find()) {
            return null;
        }
        return switch (m.group(1).toUpperCase(java.util.Locale.ROOT)) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            default -> null;
        };
    }

    /**
     * The AI's box for a drawing, with a small margin, or empty when it cannot be trusted: not four numbers
     * {@code [ymin, xmin, ymax, xmax]} within 0-1000, upside down, too thin to be a drawing, or on a page not sent.
     * An empty box keeps the old behaviour - the teacher adds the picture by hand.
     */
    static List<Integer> figureBox(List<Integer> box, int figurePage, int pageCount) {
        if (box == null || box.size() != 4 || box.stream().anyMatch(Objects::isNull)
                || figurePage < 1 || figurePage > pageCount) {
            return List.of();
        }
        int ymin = box.get(0), xmin = box.get(1), ymax = box.get(2), xmax = box.get(3);
        if (ymin < 0 || xmin < 0 || ymax > 1000 || xmax > 1000 || ymax - ymin < MIN_FIGURE || xmax - xmin < MIN_FIGURE) {
            return List.of();
        }
        return List.of(Math.max(0, ymin - FIGURE_MARGIN), Math.max(0, xmin - FIGURE_MARGIN),
                Math.min(1000, ymax + FIGURE_MARGIN), Math.min(1000, xmax + FIGURE_MARGIN));
    }

    /**
     * The EN headings as printed: "SUBIECTUL II-lea" (a reading that dropped the word) becomes "SUBIECTUL al II-lea";
     * "SUBIECTUL I" stays as it is.
     */
    static String enHeading(String section) {
        return section == null ? null : EN_HEADING.matcher(section).replaceFirst("SUBIECTUL al $1-lea");
    }

    private static final Pattern EN_HEADING = Pattern.compile("^SUBIECTUL\\s+(?:AL\\s+)?([IVX]{2,4})-LEA", Pattern.CASE_INSENSITIVE);

    /** "a) Arată că..." / "(2p) b) Calculează..." -> the text alone: the label comes from the order, the points apart. */
    static String withoutPartLabel(String statement) {
        return statement == null ? null : PART_LABEL.matcher(statement).replaceFirst("");
    }

    private static final Pattern PART_LABEL = Pattern.compile("^\\s*(?:\\(\\s*\\d{1,3}\\s*p\\s*\\)\\s*)?[a-f]\\)\\s+");

    /** An even number of unescaped {@code $}: every inline formula is closed ({@code $$} counts as two). */
    static boolean balancedMath(String text) {
        int dollars = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\\') {
                i++; // "\$" is a literal dollar sign
            } else if (text.charAt(i) == '$') {
                dollars++;
            }
        }
        return dollars % 2 == 0;
    }

    /**
     * "1. Calculați..." -> "Calculați...": the quiz numbers its questions itself. Only a number followed by "." or ")"
     * and a space is dropped, so "2.5 este" or "3 + 4" stay as they are.
     */
    static String withoutNumber(String statement) {
        return statement == null ? null : LEADING_NUMBER.matcher(statement).replaceFirst("");
    }

    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^\\s*(?:(?i:ex(?:ercițiul|\\.)?)\\s*)?\\d{1,3}\\s*[.)]\\s+");

    private static String trim(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cap(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
