package ro.mathlms.mastery;

import org.junit.jupiter.api.Test;
import ro.mathlms.mastery.MasteryCalculator.Answer;
import ro.mathlms.mastery.MasteryCalculator.Tally;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MasteryCalculatorTest {

    private static final Long ANA = 1L;
    private static final Long BOB = 2L;
    private static final Long FRACTII = 10L;
    private static final Long ECUATII = 11L;

    private static Instant day(int d) {
        return Instant.parse("2026-10-01T10:00:00Z").plusSeconds(d * 86_400L);
    }

    private static Answer answer(Long student, long item, Long chapter, int points, Integer earned, int day) {
        return new Answer(student, item, chapter, points, earned, day(day));
    }

    @Test
    void onlyTheLatestAnswerToEachQuestionCounts() {
        Map<Long, Map<Long, Tally>> result = MasteryCalculator.latestPerQuestion(List.of(
                answer(ANA, 100, FRACTII, 2, 0, 1),   // wrong in the test...
                answer(ANA, 100, FRACTII, 2, 2, 3))); // ...right after practising

        assertThat(result.get(ANA).get(FRACTII)).isEqualTo(new Tally(2, 2, 1));
        assertThat(result.get(ANA).get(FRACTII).percent()).isEqualTo(100);
    }

    @Test
    void theOrderTheAnswersArriveInDoesNotMatter() {
        Map<Long, Map<Long, Tally>> result = MasteryCalculator.latestPerQuestion(List.of(
                answer(ANA, 100, FRACTII, 2, 2, 3),
                answer(ANA, 100, FRACTII, 2, 0, 1)));

        assertThat(result.get(ANA).get(FRACTII).earned()).isEqualTo(2);
    }

    @Test
    void aChapterIsPointsEarnedOverPointsPossibleAcrossItsQuestions() {
        Map<Long, Map<Long, Tally>> result = MasteryCalculator.latestPerQuestion(List.of(
                answer(ANA, 100, FRACTII, 2, 2, 1),
                answer(ANA, 101, FRACTII, 5, 0, 1),
                answer(ANA, 102, FRACTII, 4, 3, 2),
                answer(ANA, 103, ECUATII, 3, 3, 2)));

        Tally fractii = result.get(ANA).get(FRACTII);
        assertThat(fractii).isEqualTo(new Tally(5, 11, 3));
        assertThat(fractii.percent()).isEqualTo(45); // 5 / 11 = 45.45...
        assertThat(result.get(ANA).get(ECUATII).percent()).isEqualTo(100);
    }

    @Test
    void anAnswerWaitingForTheTeacherIsSkippedAndTheEarlierScoredOneStands() {
        Map<Long, Map<Long, Tally>> result = MasteryCalculator.latestPerQuestion(List.of(
                answer(ANA, 100, FRACTII, 4, 1, 1),
                answer(ANA, 100, FRACTII, 4, null, 5))); // handed in, not graded yet

        assertThat(result.get(ANA).get(FRACTII)).isEqualTo(new Tally(1, 4, 1));
    }

    @Test
    void aQuestionWithoutAChapterCountsNowhere() {
        Map<Long, Map<Long, Tally>> result = MasteryCalculator.latestPerQuestion(List.of(
                answer(ANA, 100, null, 2, 2, 1)));

        assertThat(result).isEmpty();
    }

    @Test
    void aChapterWorthNoPointsHasNoPercentButStillCountsItsQuestions() {
        Tally tally = MasteryCalculator.latestPerQuestion(List.of(answer(ANA, 100, FRACTII, 0, 0, 1)))
                .get(ANA).get(FRACTII);

        assertThat(tally.questions()).isEqualTo(1);
        assertThat(tally.percent()).isNull();
    }

    @Test
    void eachStudentIsCountedOnTheirOwn() {
        Map<Long, Map<Long, Tally>> result = MasteryCalculator.latestPerQuestion(List.of(
                answer(ANA, 100, FRACTII, 2, 2, 1),
                answer(BOB, 100, FRACTII, 2, 0, 2)));

        assertThat(result.get(ANA).get(FRACTII).percent()).isEqualTo(100);
        assertThat(result.get(BOB).get(FRACTII).percent()).isZero();
    }
}
