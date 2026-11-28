package ro.mathlms.studyplan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * „Planul până la EN": the weeks between today and the exam, and what to do in each, built from the student's level
 * per EN chapter and how much each chapter would add to their estimated grade. Kept free of Spring, so every rule is
 * tested on its own. Nothing is stored: the plan is worked out again on every request, so it follows the student's
 * answers - a chapter they have caught up on drops out by itself.
 *
 * <p>The weeks are the next seven days at a time, starting today; the last one ends on the exam day. A week that starts
 * at most {@value #FINAL_DAYS} days before the exam is a final week: full mock papers ({@link ItemType#MOCK}) and the
 * daily review. The weeks before are study weeks, filled by mode:
 * <ul>
 *   <li>{@link Mode#CATCH_UP} (below the target): the teacher's pins first, then the chapters by gain × 1.5 when the
 *   student has "Știu sigur" mistakes there (× 1 otherwise), taken until their gains cover target - grade (all of
 *   them if they never do). {@code ceil(chapters / study weeks)} a week, at least {@value #MIN_PER_WEEK}, at most
 *   {@value #MAX_PER_WEEK} - with too few weeks left, the last chapters do not fit.</li>
 *   <li>{@link Mode#MAINTENANCE} (at or above the target): pins first, then one chapter a week, the weakest.</li>
 *   <li>{@link Mode#NEEDS_ANSWERS} (no estimate yet): only this week - the daily review and the two biggest gains.</li>
 * </ul>
 * A chapter is a lesson (when the student is under {@value #LESSON_BELOW}% there or has never met it), then practice
 * ({@value #PRACTICE_QUESTIONS} questions), then a mini test. A study week left without a chapter is the daily review.
 */
public final class StudyPlanGenerator {

    static final int FINAL_DAYS = 21;
    static final int MIN_PER_WEEK = 2;
    static final int MAX_PER_WEEK = 4;
    static final int LESSON_BELOW = 40;
    static final double SURE_MISTAKES_BOOST = 0.5;
    /** Two rounds of the chapter's practice page, which asks five grile at a time. */
    public static final int PRACTICE_QUESTIONS = 10;
    /** The level a never-met chapter counts at, as in the grade estimate. */
    private static final int UNSEEN_PERCENT = 30;
    /** Sums of gains rounded to 0.1 are not exact in binary: 0.6 + 0.3 must cover 0.9. */
    private static final double EPSILON = 1e-9;

    public enum Mode {
        /** The teacher has not set the exam date yet. */
        NO_EXAM_DATE,
        /** The exam date has passed. */
        EXAM_OVER,
        /** The student has not picked a target grade. */
        NO_TARGET,
        /** Not enough answers for an estimate yet. */
        NEEDS_ANSWERS,
        /** At most {@value #FINAL_DAYS} days left: mock papers and review only. */
        FINAL,
        /** Already at (or above) the target. */
        MAINTENANCE,
        /** Below the target. */
        CATCH_UP
    }

    public enum ItemType {
        LESSON, PRACTICE, MINI_TEST, MOCK, REVIEW
    }

    /**
     * One EN chapter as the plan sees it. {@code percent} null = never met; {@code gain} = how much the estimated
     * grade would rise if the student mastered it; {@code sureMistakes} = wrong answers given with "Știu sigur".
     */
    public record ChapterInput(Long chapterId, String code, String title, Integer percent, double gain,
                               boolean sureMistakes, boolean pinned) {
    }

    /** Something to do. {@code chapterId}, {@code code} and {@code title} are null for a mock paper and the review. */
    public record Item(ItemType type, Long chapterId, String code, String title) {
    }

    /** {@code finalWeek}: one of the last weeks, mock papers and review. */
    public record Week(int number, LocalDate start, LocalDate end, boolean finalWeek, List<Item> items) {
    }

    /** {@code daysLeft} null when there is no exam date. */
    public record StudyPlan(Double grade, Double target, Integer daysLeft, Mode mode, List<Week> weeks) {
    }

    private StudyPlanGenerator() {
    }

    public static StudyPlan generate(LocalDate today, LocalDate examDate, Double grade, Double target,
                                     List<ChapterInput> chapters) {
        if (examDate == null) {
            return new StudyPlan(grade, target, null, Mode.NO_EXAM_DATE, List.of());
        }
        int daysLeft = (int) ChronoUnit.DAYS.between(today, examDate);
        if (daysLeft < 0) {
            return new StudyPlan(grade, target, daysLeft, Mode.EXAM_OVER, List.of());
        }
        if (daysLeft <= FINAL_DAYS) {
            return new StudyPlan(grade, target, daysLeft, Mode.FINAL, weeks(today, examDate, daysLeft, List.of(), 0));
        }
        if (target == null) {
            return new StudyPlan(grade, target, daysLeft, Mode.NO_TARGET, List.of());
        }
        if (grade == null) {
            List<ChapterInput> first = pinsFirst(chapters, byGain()).stream()
                    .filter(c -> c.pinned() || c.gain() > 0).limit(MIN_PER_WEEK).toList();
            List<Item> items = new ArrayList<>();
            items.add(new Item(ItemType.REVIEW, null, null, null));
            first.forEach(c -> items.addAll(itemsFor(c)));
            return new StudyPlan(grade, target, daysLeft, Mode.NEEDS_ANSWERS,
                    List.of(new Week(1, today, today.plusDays(6), false, items)));
        }
        if (grade >= target) {
            List<ChapterInput> weakest = pinsFirst(chapters, weakestFirst()).stream()
                    .filter(c -> c.pinned() || c.gain() > 0).toList();
            return new StudyPlan(grade, target, daysLeft, Mode.MAINTENANCE,
                    weeks(today, examDate, daysLeft, weakest, 1));
        }
        List<ChapterInput> chosen = chosenToCover(chapters, target - grade);
        int studyWeeks = studyWeeks(today, examDate, daysLeft);
        int perWeek = Math.clamp((chosen.size() + studyWeeks - 1) / studyWeeks, MIN_PER_WEEK, MAX_PER_WEEK);
        return new StudyPlan(grade, target, daysLeft, Mode.CATCH_UP, weeks(today, examDate, daysLeft, chosen, perWeek));
    }

    /** Every pin, then the rest by gain (× 1.5 with "Știu sigur" mistakes) until the gains cover {@code gap}. */
    private static List<ChapterInput> chosenToCover(List<ChapterInput> chapters, double gap) {
        List<ChapterInput> chosen = new ArrayList<>();
        double covered = 0;
        for (ChapterInput c : pinsFirst(chapters, byScore())) {
            if (c.pinned()) {
                chosen.add(c);
                covered += c.gain();
            } else if (c.gain() > 0 && covered < gap - EPSILON) {
                chosen.add(c);
                covered += c.gain();
            }
        }
        return chosen;
    }

    /** The weeks up to the exam: {@code perWeek} chapters in each study week, in order; mock papers in the final ones. */
    private static List<Week> weeks(LocalDate today, LocalDate examDate, int daysLeft, List<ChapterInput> chapters,
                                    int perWeek) {
        List<Week> weeks = new ArrayList<>();
        int next = 0;
        for (int number = 1; number <= daysLeft / 7 + 1; number++) {
            LocalDate start = today.plusDays(7L * (number - 1));
            LocalDate end = start.plusDays(6).isAfter(examDate) ? examDate : start.plusDays(6);
            List<Item> items = new ArrayList<>();
            boolean finalWeek = isFinal(start, examDate);
            if (finalWeek) {
                items.add(new Item(ItemType.MOCK, null, null, null));
                items.add(new Item(ItemType.REVIEW, null, null, null));
            } else {
                for (int i = 0; i < perWeek && next < chapters.size(); i++) {
                    items.addAll(itemsFor(chapters.get(next++)));
                }
                if (items.isEmpty()) {
                    items.add(new Item(ItemType.REVIEW, null, null, null));
                }
            }
            weeks.add(new Week(number, start, end, finalWeek, List.copyOf(items)));
        }
        return weeks;
    }

    private static int studyWeeks(LocalDate today, LocalDate examDate, int daysLeft) {
        int count = 0;
        for (int number = 1; number <= daysLeft / 7 + 1; number++) {
            if (!isFinal(today.plusDays(7L * (number - 1)), examDate)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isFinal(LocalDate weekStart, LocalDate examDate) {
        return ChronoUnit.DAYS.between(weekStart, examDate) <= FINAL_DAYS;
    }

    /** The lesson first when the chapter is new or under {@value #LESSON_BELOW}%, then practice, then a mini test. */
    private static List<Item> itemsFor(ChapterInput c) {
        List<Item> items = new ArrayList<>();
        if (c.percent() == null || c.percent() < LESSON_BELOW) {
            items.add(new Item(ItemType.LESSON, c.chapterId(), c.code(), c.title()));
        }
        items.add(new Item(ItemType.PRACTICE, c.chapterId(), c.code(), c.title()));
        items.add(new Item(ItemType.MINI_TEST, c.chapterId(), c.code(), c.title()));
        return items;
    }

    private static List<ChapterInput> pinsFirst(List<ChapterInput> chapters, Comparator<ChapterInput> order) {
        return chapters.stream()
                .sorted(Comparator.comparing((ChapterInput c) -> !c.pinned()).thenComparing(order)
                        .thenComparing(ChapterInput::code, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Comparator<ChapterInput> byGain() {
        return Comparator.comparingDouble(ChapterInput::gain).reversed();
    }

    private static Comparator<ChapterInput> byScore() {
        return Comparator.comparingDouble((ChapterInput c) -> c.gain() * (c.sureMistakes() ? 1 + SURE_MISTAKES_BOOST : 1))
                .reversed();
    }

    /** The lowest level first (a never-met chapter at {@value #UNSEEN_PERCENT}%); on a tie, the bigger gain. */
    private static Comparator<ChapterInput> weakestFirst() {
        return Comparator.comparingInt((ChapterInput c) -> c.percent() == null ? UNSEEN_PERCENT : c.percent())
                .thenComparing(byGain());
    }
}
