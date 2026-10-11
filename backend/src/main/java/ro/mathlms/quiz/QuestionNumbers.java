package ro.mathlms.quiz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The number a question shows on screen: "3" in a plain test, "I.3" under a "SUBIECTUL I" heading - numbering starts
 * again under each heading. The same rule as the frontend's {@code numberItems} ({@code lib/sections.ts}), so the
 * journal names a question the way the teacher sees it.
 */
final class QuestionNumbers {

    private static final Pattern ROMAN = Pattern.compile("^subiectul\\s+(?:al\\s+)?([ivxlc]+)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private QuestionNumbers() {
    }

    /** Item id → its on-screen number, for the items of one quiz in position order. */
    static Map<Long, String> of(List<QuizItem> items) {
        Map<Long, String> numbers = new HashMap<>();
        boolean sectioned = items.stream().anyMatch(i -> i.getSection() != null);
        int group = 0;
        int local = 0;
        String previous = null;
        for (int index = 0; index < items.size(); index++) {
            QuizItem item = items.get(index);
            if (!sectioned) {
                numbers.put(item.getId(), String.valueOf(index + 1));
                continue;
            }
            String section = item.getSection();
            if (index == 0 || !Objects.equals(section, previous)) {
                group++;
                local = 0;
            }
            previous = section;
            local++;
            if (section == null) {
                numbers.put(item.getId(), String.valueOf(local));
            } else {
                String title = section.split("\n", 2)[0].strip();
                Matcher roman = ROMAN.matcher(title);
                String prefix = roman.find() ? roman.group(1).toUpperCase() : String.valueOf(group);
                numbers.put(item.getId(), prefix + "." + local);
            }
        }
        return numbers;
    }

    /** The number of one item within its quiz's current items; falls back to position + 1. */
    static String of(QuizItem item, List<QuizItem> quizItems) {
        String number = of(quizItems).get(item.getId());
        return number != null ? number : String.valueOf(item.getPosition() + 1);
    }
}
