package ro.mathlms.studyplan;

import org.junit.jupiter.api.Test;
import ro.mathlms.studyplan.StudyPlanGenerator.ChapterInput;
import ro.mathlms.studyplan.StudyPlanGenerator.Item;
import ro.mathlms.studyplan.StudyPlanGenerator.ItemType;
import ro.mathlms.studyplan.StudyPlanGenerator.Mode;
import ro.mathlms.studyplan.StudyPlanGenerator.StudyPlan;
import ro.mathlms.studyplan.StudyPlanGenerator.Week;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** „Planul până la EN": which chapters, in which week, and what to do in each - one test per rule. */
class StudyPlanGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);

    private static ChapterInput chapter(long id, Integer percent, double gain) {
        return new ChapterInput(id, "A" + id, "Capitol " + id, percent, gain, false, false);
    }

    private static ChapterInput sure(ChapterInput c) {
        return new ChapterInput(c.chapterId(), c.code(), c.title(), c.percent(), c.gain(), true, c.pinned());
    }

    private static ChapterInput pinned(ChapterInput c) {
        return new ChapterInput(c.chapterId(), c.code(), c.title(), c.percent(), c.gain(), c.sureMistakes(), true);
    }

    private static StudyPlan plan(int daysLeft, Double grade, Double target, List<ChapterInput> chapters) {
        return StudyPlanGenerator.generate(TODAY, TODAY.plusDays(daysLeft), grade, target, chapters);
    }

    /** The chapters of every study week, in order (the weeks before the last three). */
    private static List<List<Long>> chaptersPerWeek(StudyPlan plan) {
        return plan.weeks().stream().filter(w -> !w.finalWeek())
                .map(w -> w.items().stream().map(Item::chapterId).filter(id -> id != null).distinct().toList())
                .toList();
    }

    private static List<Long> plannedChapters(StudyPlan plan) {
        return chaptersPerWeek(plan).stream().flatMap(List::stream).toList();
    }

    private static List<ItemType> typesOf(StudyPlan plan, long chapterId) {
        return plan.weeks().stream().flatMap(w -> w.items().stream())
                .filter(i -> Long.valueOf(chapterId).equals(i.chapterId())).map(Item::type).toList();
    }

    // --- the weeks themselves ---

    @Test
    void weeksAreTheNextSevenDaysAtATimeUpToTheExamDay() {
        StudyPlan p = plan(94, 7.4, 9.0, List.of(chapter(1, 50, 0.5)));

        assertThat(p.daysLeft()).isEqualTo(94);
        assertThat(p.weeks()).hasSize(14); // 13 full weeks + the exam's own few days
        Week first = p.weeks().getFirst();
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.start()).isEqualTo(TODAY);
        assertThat(first.end()).isEqualTo(TODAY.plusDays(6));
        assertThat(p.weeks().getLast().end()).isEqualTo(TODAY.plusDays(94));
    }

    // --- rule: the last 21 days are mock papers and review ---

    @Test
    void inTheLastThreeWeeksThePlanIsOnlyMockPapersAndReview() {
        StudyPlan p = plan(21, 6.0, 9.0, List.of(chapter(1, 20, 1.0), pinned(chapter(2, 30, 0.8))));

        assertThat(p.mode()).isEqualTo(Mode.FINAL);
        assertThat(p.weeks()).hasSize(4).allSatisfy(w -> {
            assertThat(w.finalWeek()).isTrue();
            assertThat(w.items()).extracting(Item::type).containsExactly(ItemType.MOCK, ItemType.REVIEW);
        });
    }

    @Test
    void aLongPlanAlsoEndsWithThreeWeeksOfMockPapers() {
        StudyPlan p = plan(94, 7.4, 9.0, List.of(chapter(1, 50, 0.5), chapter(2, 50, 0.5), chapter(3, 50, 0.5)));

        assertThat(p.mode()).isEqualTo(Mode.CATCH_UP);
        List<Week> finals = p.weeks().stream().filter(Week::finalWeek).toList();
        assertThat(finals).hasSize(3); // days 77-83, 84-90, 91-94: each starts at most 21 days before the exam
        assertThat(finals).allSatisfy(w ->
                assertThat(w.items()).extracting(Item::type).containsExactly(ItemType.MOCK, ItemType.REVIEW));
        assertThat(p.weeks().subList(0, 11)).noneMatch(Week::finalWeek); // day 70 is 24 days before: still study
    }

    // --- rule: at or above the target, one chapter a week, the weakest ---

    @Test
    void atTheTargetTheyKeepItUpWithOneChapterAWeekWeakestFirst() {
        StudyPlan p = plan(60, 9.2, 9.0, List.of(chapter(1, 80, 0.2), chapter(2, 55, 0.4), chapter(3, 70, 0.3)));

        assertThat(p.mode()).isEqualTo(Mode.MAINTENANCE);
        assertThat(chaptersPerWeek(p).subList(0, 3)).containsExactly(List.of(2L), List.of(3L), List.of(1L));
        // Once every weak chapter had its week, the rest is the daily review.
        assertThat(p.weeks().get(3).items()).extracting(Item::type).containsExactly(ItemType.REVIEW);
    }

    // --- rule: below the target, the chapters whose gains cover the gap ---

    @Test
    void belowTheTargetItTakesTheBiggestGainsUntilTheyCoverTheGap() {
        // 9.0 - 8.0 = 1.0 to make up: 0.6 + 0.3 = 0.9 is not enough yet, + 0.2 = 1.1 is.
        StudyPlan p = plan(90, 8.0, 9.0, List.of(chapter(1, 50, 0.3), chapter(2, 40, 0.6), chapter(3, 60, 0.2),
                chapter(4, 70, 0.1), chapter(5, 80, 0.1)));

        assertThat(p.mode()).isEqualTo(Mode.CATCH_UP);
        assertThat(plannedChapters(p)).containsExactly(2L, 1L, 3L);
    }

    @Test
    void aConfidentMistakeMakesAChapterWorthHalfAsMuchAgain() {
        // 0.4 × 1.5 = 0.6 beats 0.5: "Știu sigur" and wrong is a misconception, fix it first.
        StudyPlan p = plan(90, 8.0, 9.0, List.of(chapter(1, 50, 0.5), sure(chapter(2, 60, 0.4))));

        assertThat(plannedChapters(p)).containsExactly(2L, 1L);
    }

    @Test
    void whenNothingCoversTheGapEveryChapterWithAGainIsPlanned() {
        StudyPlan p = plan(90, 5.0, 10.0, List.of(chapter(1, 50, 0.5), chapter(2, 60, 0.4), chapter(3, 100, 0.0)));

        assertThat(plannedChapters(p)).containsExactly(1L, 2L);
    }

    @Test
    void aChapterTheTeacherPinnedComesFirst() {
        StudyPlan p = plan(90, 8.0, 9.0, List.of(chapter(1, 40, 0.6), chapter(2, 50, 0.5), pinned(chapter(3, 90, 0.1))));

        assertThat(plannedChapters(p)).containsExactly(3L, 1L, 2L);
    }

    @Test
    void aPinnedChapterCountsTowardsTheGapLikeAnyOther() {
        // 1.0 to make up: the pin's 0.5 + 0.6 covers it, so chapter 2 is not needed.
        StudyPlan p = plan(90, 8.0, 9.0, List.of(chapter(1, 40, 0.6), chapter(2, 50, 0.45), pinned(chapter(3, 60, 0.5))));

        assertThat(plannedChapters(p)).containsExactly(3L, 1L);
    }

    @Test
    void aPinIsKeptEvenWhenItAddsNothingToTheEstimate() {
        // The teacher may pin a chapter that is not on the paper (gain 0): it is their call.
        StudyPlan p = plan(90, 8.0, 9.0, List.of(chapter(1, 40, 1.2), pinned(chapter(2, 95, 0.0))));

        assertThat(plannedChapters(p)).containsExactly(2L, 1L);
    }

    // --- rule: chapters per week = ceil(chapters / study weeks), between 2 and 4 ---

    @Test
    void atLeastTwoChaptersAWeek() {
        // 3 chapters over 10 study weeks would be 1 a week: 2 is the floor.
        StudyPlan p = plan(94, 6.0, 9.0, List.of(chapter(1, 10, 1.0), chapter(2, 10, 1.0), chapter(3, 10, 1.0)));

        assertThat(chaptersPerWeek(p).subList(0, 3)).containsExactly(List.of(1L, 2L), List.of(3L), List.of());
    }

    @Test
    void chaptersAreSpreadEvenlyOverTheStudyWeeks() {
        // 9 chapters over 3 study weeks (days 0-6, 7-13, 14-20; the exam is on day 42): 3 a week.
        List<ChapterInput> nine = java.util.stream.LongStream.rangeClosed(1, 9)
                .mapToObj(id -> chapter(id, 30, 1.0 - id / 100.0)).toList();
        StudyPlan p = plan(42, 2.0, 10.0, nine);

        assertThat(chaptersPerWeek(p)).containsExactly(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L), List.of(7L, 8L, 9L));
    }

    @Test
    void atMostFourChaptersAWeekEvenIfSomeDoNotFit() {
        List<ChapterInput> ten = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> chapter(id, 30, 1.0 - id / 100.0)).toList();
        StudyPlan p = plan(28, 2.0, 10.0, ten); // one study week (days 0-6), then the final three

        assertThat(chaptersPerWeek(p)).containsExactly(List.of(1L, 2L, 3L, 4L));
    }

    // --- rule: what to do in each chapter ---

    @Test
    void anUntouchedChapterStartsWithTheLesson() {
        StudyPlan p = plan(90, 6.0, 9.0, List.of(chapter(1, null, 1.0)));

        assertThat(typesOf(p, 1)).containsExactly(ItemType.LESSON, ItemType.PRACTICE, ItemType.MINI_TEST);
    }

    @Test
    void underFortyPercentTheLessonComesFirstFromFortyStraightToPractice() {
        StudyPlan p = plan(90, 6.0, 9.0, List.of(chapter(1, 39, 1.0), chapter(2, 40, 0.9)));

        assertThat(typesOf(p, 1)).containsExactly(ItemType.LESSON, ItemType.PRACTICE, ItemType.MINI_TEST);
        assertThat(typesOf(p, 2)).containsExactly(ItemType.PRACTICE, ItemType.MINI_TEST);
    }

    // --- nothing to plan yet ---

    @Test
    void withoutAnExamDateThereIsNoPlan() {
        StudyPlan p = StudyPlanGenerator.generate(TODAY, null, 7.0, 9.0, List.of(chapter(1, 50, 0.5)));

        assertThat(p.mode()).isEqualTo(Mode.NO_EXAM_DATE);
        assertThat(p.daysLeft()).isNull();
        assertThat(p.weeks()).isEmpty();
    }

    @Test
    void afterTheExamThereIsNoPlan() {
        StudyPlan p = plan(-1, 7.0, 9.0, List.of(chapter(1, 50, 0.5)));

        assertThat(p.mode()).isEqualTo(Mode.EXAM_OVER);
        assertThat(p.weeks()).isEmpty();
    }

    @Test
    void withoutATargetThereIsNoPlanYet() {
        StudyPlan p = plan(90, 7.0, null, List.of(chapter(1, 50, 0.5)));

        assertThat(p.mode()).isEqualTo(Mode.NO_TARGET);
        assertThat(p.weeks()).isEmpty();
    }

    @Test
    void beforeTheEstimateOnlyTheFirstWeekIsPlannedWithTheBiggestGains() {
        StudyPlan p = plan(90, null, 9.0, List.of(chapter(1, null, 0.4), chapter(2, null, 0.9), chapter(3, null, 0.6)));

        assertThat(p.mode()).isEqualTo(Mode.NEEDS_ANSWERS);
        assertThat(p.weeks()).hasSize(1);
        assertThat(p.weeks().getFirst().items()).extracting(Item::type).startsWith(ItemType.REVIEW);
        assertThat(plannedChapters(p)).containsExactly(2L, 3L);
    }
}
