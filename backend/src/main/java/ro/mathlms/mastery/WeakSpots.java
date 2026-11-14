package ro.mathlms.mastery;

import ro.mathlms.mastery.MasteryCalculator.Answer;
import ro.mathlms.mastery.MasteryCalculator.Tally;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * „La ce stau prost?": the student's weakest EN chapters, from their own answers only - no model is asked. A chapter is
 * weak below {@value #WEAK_BELOW}% of its points (the latest answer to each question, as on the map), and only once it
 * rests on at least {@value #MIN_QUESTIONS} questions (one slip says nothing). Weakest first; on a tie, the one with more
 * "Știu sigur" mistakes (a confident error is a misconception, not a guess), then the one with more questions. Each
 * comes with what explains it: the answers of the last {@value #RECENT_DAYS} days and when it was last worked on.
 */
final class WeakSpots {

    static final int SIZE = 3;
    static final int WEAK_BELOW = 70;
    static final int MIN_QUESTIONS = 2;
    static final int RECENT_DAYS = 14;

    /** One weak chapter and the numbers it is explained with. */
    record Spot(Long chapterId, int percent, int questions, int recentWrong, int recentAnswered, int sureWrong,
                Instant lastAnsweredAt) {
    }

    private WeakSpots() {
    }

    /** {@code sureWrong}: per chapter, the review answers given with "Știu sigur" that were wrong. */
    static List<Spot> rank(List<Answer> answers, Map<Long, Integer> sureWrong, Instant now) {
        Map<Long, Tally> levels = MasteryCalculator.latestPerQuestion(answers).values().stream()
                .findFirst().orElse(Map.of()); // one student
        Instant since = now.minus(Duration.ofDays(RECENT_DAYS));
        Map<Long, int[]> recent = new HashMap<>(); // chapter -> {wrong, answered}
        Map<Long, Instant> last = new HashMap<>();
        for (Answer a : answers) {
            if (a.chapterId() == null || a.earned() == null) {
                continue;
            }
            last.merge(a.chapterId(), a.at(), (x, y) -> x.isAfter(y) ? x : y);
            if (!a.at().isBefore(since)) {
                int[] counts = recent.computeIfAbsent(a.chapterId(), k -> new int[2]);
                counts[1]++;
                if (a.earned() < a.points()) {
                    counts[0]++;
                }
            }
        }
        return levels.entrySet().stream()
                .filter(e -> e.getValue().percent() != null && e.getValue().percent() < WEAK_BELOW
                        && e.getValue().questions() >= MIN_QUESTIONS)
                .map(e -> {
                    int[] counts = recent.getOrDefault(e.getKey(), new int[2]);
                    return new Spot(e.getKey(), e.getValue().percent(), e.getValue().questions(), counts[0], counts[1],
                            sureWrong.getOrDefault(e.getKey(), 0), last.get(e.getKey()));
                })
                .sorted(Comparator.comparingInt(Spot::percent)
                        .thenComparing(Comparator.comparingInt(Spot::sureWrong).reversed())
                        .thenComparing(Comparator.comparingInt(Spot::questions).reversed())
                        .thenComparing(Spot::chapterId))
                .limit(SIZE)
                .toList();
    }
}
