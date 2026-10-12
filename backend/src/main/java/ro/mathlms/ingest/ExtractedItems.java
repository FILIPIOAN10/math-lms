package ro.mathlms.ingest;

import ro.mathlms.quiz.QuizItem;
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
    static final String TOO_FEW_OPTIONS = "Grilă cu mai puțin de 2 variante - completează variantele sau fă-l subiect deschis.";
    static final String FIGURE = "Are un desen: adaugă-l ca imagine după ce creezi testul.";

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
            if (item.hasFigure()) {
                warnings.add(FIGURE);
            }
            Integer points = item.points() == null || item.points() < 0 || item.points() > MAX_POINTS ? null : item.points();
            int page = Math.clamp(item.page(), 1, Math.max(1, pageCount));
            out.add(new ExtractedItem(page, cap(trim(item.section()), QuizItem.MAX_SECTION), type, statement,
                    List.copyOf(options), points, item.hasFigure(), List.copyOf(warnings)));
        }
        return out;
    }

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
