package ro.mathlms.mastery;

import org.junit.jupiter.api.Test;
import ro.mathlms.mastery.MasteryCalculator.Answer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** „La ce stau prost?": which EN chapters come first, and the numbers each one is explained with. */
class WeakSpotsTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static Answer answer(long item, long chapter, boolean right, int daysAgo) {
        return new Answer(1L, item, chapter, 5, right ? 5 : 0, NOW.minus(Duration.ofDays(daysAgo)));
    }

    private static List<Answer> answers(long chapter, long firstItem, int right, int wrong, int daysAgo) {
        List<Answer> list = new ArrayList<>();
        long item = firstItem;
        for (int i = 0; i < right; i++) {
            list.add(answer(item++, chapter, true, daysAgo));
        }
        for (int i = 0; i < wrong; i++) {
            list.add(answer(item++, chapter, false, daysAgo));
        }
        return list;
    }

    @Test
    void theWeakestChaptersComeFirstAtMostThree() {
        List<Answer> all = new ArrayList<>();
        all.addAll(answers(10, 100, 1, 3, 2));  // 25%
        all.addAll(answers(20, 200, 0, 2, 2));  // 0%
        all.addAll(answers(30, 300, 1, 1, 2));  // 50%
        all.addAll(answers(40, 400, 2, 3, 2));  // 40%
        all.addAll(answers(50, 500, 4, 0, 2));  // 100%: not weak

        List<WeakSpots.Spot> spots = WeakSpots.rank(all, Map.of(), NOW);

        assertThat(spots).extracting(WeakSpots.Spot::chapterId).containsExactly(20L, 10L, 40L);
        assertThat(spots.getFirst().percent()).isZero();
        assertThat(spots.getFirst().questions()).isEqualTo(2);
    }

    @Test
    void aChapterAtSeventyPercentOrAboveIsNotWeak() {
        List<Answer> all = new ArrayList<>(answers(10, 100, 7, 3, 1)); // 70%

        assertThat(WeakSpots.rank(all, Map.of(), NOW)).isEmpty();
    }

    @Test
    void oneQuestionIsTooLittleToSay() {
        assertThat(WeakSpots.rank(answers(10, 100, 0, 1, 1), Map.of(), NOW)).isEmpty();
    }

    @Test
    void theLatestAnswerToAQuestionCountsForTheLevelButEveryRecentAnswerIsCounted() {
        List<Answer> all = new ArrayList<>(List.of(
                answer(100, 10, false, 5), answer(100, 10, true, 1), // got it right the second time
                answer(101, 10, false, 3), answer(102, 10, false, 40)));

        WeakSpots.Spot spot = WeakSpots.rank(all, Map.of(), NOW).getFirst();

        assertThat(spot.percent()).isEqualTo(33); // 1 of 3 questions, latest answers
        assertThat(spot.recentAnswered()).isEqualTo(3); // the last 14 days: three answers
        assertThat(spot.recentWrong()).isEqualTo(2);
        assertThat(spot.lastAnsweredAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void onATieTheConfidentMistakesComeFirst() {
        List<Answer> all = new ArrayList<>();
        all.addAll(answers(10, 100, 1, 1, 2));
        all.addAll(answers(20, 200, 1, 1, 2));

        List<WeakSpots.Spot> spots = WeakSpots.rank(all, Map.of(20L, 2), NOW);

        assertThat(spots).extracting(WeakSpots.Spot::chapterId).containsExactly(20L, 10L);
        assertThat(spots.getFirst().sureWrong()).isEqualTo(2);
    }
}
