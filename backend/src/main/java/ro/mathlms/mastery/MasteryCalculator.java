package ro.mathlms.mastery;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mastery rule, with no database in sight: for each student and question only the LATEST scored answer counts, and
 * a chapter's level is the points earned over the points possible across its questions. Tests and practice both feed
 * it (the caller decides what a scored answer is); answers without a chapter, or still waiting for a score, are ignored.
 */
public final class MasteryCalculator {

    private MasteryCalculator() {
    }

    /** One answer to one question. {@code earned == null} = waiting for the teacher: ignored. */
    public record Answer(Long studentId, Long itemId, Long chapterId, int points, Integer earned, Instant at) {
    }

    /** A chapter's level: points earned, points possible, and how many questions it is based on. */
    public record Tally(int earned, int possible, int questions) {

        Tally plus(Answer answer) {
            return new Tally(earned + answer.earned(), possible + answer.points(), questions + 1);
        }

        /** Rounded percent, or {@code null} when there is nothing to divide by (no question, or none worth points). */
        public Integer percent() {
            return possible == 0 ? null : Math.round(100f * earned / possible);
        }
    }

    private record Key(Long studentId, Long itemId) {
    }

    /** student id → chapter id → tally, from each student's latest scored answer to each question. */
    public static Map<Long, Map<Long, Tally>> latestPerQuestion(Collection<Answer> answers) {
        Map<Key, Answer> latest = new HashMap<>();
        answers.stream()
                .filter(a -> a.chapterId() != null && a.earned() != null)
                .sorted(Comparator.comparing(Answer::at))
                .forEach(a -> latest.put(new Key(a.studentId(), a.itemId()), a)); // later overwrites earlier

        Map<Long, Map<Long, Tally>> result = new LinkedHashMap<>();
        for (Answer a : latest.values()) {
            result.computeIfAbsent(a.studentId(), s -> new HashMap<>())
                    .merge(a.chapterId(), new Tally(0, 0, 0).plus(a), (old, one) ->
                            new Tally(old.earned() + one.earned(), old.possible() + one.possible(),
                                    old.questions() + one.questions()));
        }
        return result;
    }
}
