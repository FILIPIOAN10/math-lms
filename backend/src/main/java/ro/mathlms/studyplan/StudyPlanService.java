package ro.mathlms.studyplan;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.mastery.EnMasteryService;
import ro.mathlms.mastery.EnMasteryService.EnChapterMasteryDto;
import ro.mathlms.mastery.EnPrediction;
import ro.mathlms.mastery.EnPredictionCalculator;
import ro.mathlms.mastery.EnPredictionService;
import ro.mathlms.studyplan.EnGoalService.Goal;
import ro.mathlms.studyplan.EnGoalService.Snapshot;
import ro.mathlms.studyplan.StudyPlanGenerator.ChapterInput;
import ro.mathlms.studyplan.StudyPlanGenerator.Mode;
import ro.mathlms.studyplan.StudyPlanGenerator.StudyPlan;
import ro.mathlms.studyplan.StudyPlanGenerator.Week;
import ro.mathlms.user.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * „Planul până la EN" for one student: their level per EN chapter ({@link EnMasteryService}), the estimate and each
 * chapter's gain ({@link EnPredictionCalculator}, from the paper as the admin linked it), their "Știu sigur" mistakes,
 * their target, the teacher's pins and the exam date, put together by {@link StudyPlanGenerator}. Read-only: nothing
 * of the plan is stored.
 */
@Service
public class StudyPlanService {

    /** A chapter the teacher pinned. */
    public record PinDto(Long chapterId, String code, String title) {
    }

    /**
     * Everything the plan page shows. {@code grade}/{@code low}/{@code high} null = no estimate yet ({@code answers} of
     * {@code answersNeeded}); {@code expectedToday} = where the student should be on the way to the target (null when
     * that cannot be said); {@code history} = the estimate day by day, oldest first.
     */
    public record StudyPlanDto(LocalDate examDate, Integer daysLeft, Double target, Double grade, Double low, Double high,
                               int answers, int answersNeeded, Mode mode, boolean behind, Double expectedToday,
                               List<Week> weeks, List<Snapshot> history, List<PinDto> pins, int practiceQuestions) {
    }

    /** What the estimate card needs next to the grade: the target and the days left. */
    public record GoalSummaryDto(Double target, LocalDate examDate, Integer daysLeft) {
    }

    /** One student in the teacher's class view. */
    public record ClassGoalRowDto(Long studentId, String fullName, Double grade, Double low, Double high, Double target,
                                  Double expectedToday, boolean behind) {
    }

    private final EnMasteryService masteryService;
    private final EnPredictionService predictionService;
    private final EnGoalService goalService;
    private final EnChapterRepository chapterRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;

    public StudyPlanService(EnMasteryService masteryService, EnPredictionService predictionService,
                            EnGoalService goalService, EnChapterRepository chapterRepository,
                            EnrollmentRepository enrollmentRepository, SchoolClassRepository schoolClassRepository) {
        this.masteryService = masteryService;
        this.predictionService = predictionService;
        this.goalService = goalService;
        this.chapterRepository = chapterRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    @Transactional(readOnly = true)
    public StudyPlanDto planFor(User student) {
        List<EnChapterMasteryDto> mastery = masteryService.forStudent(student);
        Map<String, Double> points = predictionService.examPoints();
        EnPrediction prediction = EnPredictionCalculator.predict(mastery, points);
        Goal goal = goalService.goal(student.getId()).orElse(null);
        LocalDate examDate = goalService.examDate().orElse(null);
        LocalDate today = goalService.today();
        List<Long> pinIds = goalService.pins(student.getId());
        Map<Long, Integer> sure = masteryService.sureMistakesPerChapter(student);

        // Every chapter with something to gain, and every pinned one even without (the teacher's call).
        Map<Long, EnPrediction.Gain> gains = EnPredictionCalculator.gainPerChapter(mastery, points).stream()
                .collect(Collectors.toMap(EnPrediction.Gain::chapterId, Function.identity()));
        Set<Long> pinned = Set.copyOf(pinIds);
        List<ChapterInput> inputs = new ArrayList<>();
        for (EnChapterMasteryDto c : mastery) {
            EnPrediction.Gain gain = gains.get(c.chapterId());
            if (gain != null || pinned.contains(c.chapterId())) {
                inputs.add(new ChapterInput(c.chapterId(), c.code(), c.title(), c.percent(),
                        gain == null ? 0 : gain.gradeGain(), sure.getOrDefault(c.chapterId(), 0) > 0,
                        pinned.contains(c.chapterId())));
            }
        }
        Double target = goal == null ? null : goal.target();
        StudyPlan plan = StudyPlanGenerator.generate(today, examDate, prediction.grade(), target, inputs);

        Map<Long, EnChapterMasteryDto> byId = mastery.stream()
                .collect(Collectors.toMap(EnChapterMasteryDto::chapterId, Function.identity()));
        List<PinDto> pins = pinIds.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(c -> new PinDto(c.chapterId(), c.code(), c.title())).toList();
        Double expected = goal == null ? null
                : GoalPace.expectedToday(goal.setOn(), goal.baseline(), examDate, goal.target(), today);
        boolean behind = goal != null
                && GoalPace.behind(goal.setOn(), goal.baseline(), examDate, goal.target(), prediction.grade(), today);
        return new StudyPlanDto(examDate, plan.daysLeft(), target, prediction.grade(), prediction.low(), prediction.high(),
                prediction.answers(), prediction.answersNeeded(), plan.mode(), behind, expected, plan.weeks(),
                goalService.history(student.getId()), pins, StudyPlanGenerator.PRACTICE_QUESTIONS);
    }

    /** The student picks (or changes) their target; the answer is their new plan. */
    @Transactional
    public StudyPlanDto setGoal(User student, BigDecimal target) {
        goalService.setGoal(student.getId(), target, predictionService.predict(student));
        return planFor(student);
    }

    /** Cheap: no answers are read. */
    @Transactional(readOnly = true)
    public GoalSummaryDto summaryFor(User student) {
        LocalDate examDate = goalService.examDate().orElse(null);
        Integer daysLeft = examDate == null ? null : (int) ChronoUnit.DAYS.between(goalService.today(), examDate);
        return new GoalSummaryDto(goalService.goal(student.getId()).map(Goal::target).orElse(null), examDate, daysLeft);
    }

    @Transactional
    public StudyPlanDto pin(User student, Long chapterId, Long pinnedBy) {
        goalService.pin(student, chapter(chapterId), pinnedBy);
        return planFor(student);
    }

    @Transactional
    public StudyPlanDto unpin(User student, Long chapterId) {
        goalService.unpin(student, chapter(chapterId));
        return planFor(student);
    }

    private EnChapter chapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ContentNotFoundException("EnChapter", chapterId));
    }

    /** Today's estimate kept for the chart (the daily job); nothing when there is no estimate yet. */
    @Transactional
    public void snapshot(User student) {
        goalService.saveSnapshot(student.getId(), goalService.today(), predictionService.predict(student));
    }

    /** The class's students (alphabetical), each with their estimate, target and „în urmă" - one read of the answers. */
    @Transactional(readOnly = true)
    public List<ClassGoalRowDto> forClass(Long classId) {
        if (!schoolClassRepository.existsById(classId)) {
            throw new ContentNotFoundException("SchoolClass", classId);
        }
        List<User> students = enrollmentRepository.findBySchoolClassIdFetchStudent(classId).stream()
                .map(Enrollment::getStudent)
                .filter(s -> !s.isErased())
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, List<EnChapterMasteryDto>> mastery = masteryService.forStudents(students);
        Map<String, Double> points = predictionService.examPoints();
        Map<Long, Goal> goals = goalService.goals(students.stream().map(User::getId).toList());
        LocalDate examDate = goalService.examDate().orElse(null);
        LocalDate today = goalService.today();
        List<ClassGoalRowDto> rows = new ArrayList<>();
        for (User s : students) {
            EnPrediction p = EnPredictionCalculator.predict(mastery.getOrDefault(s.getId(), List.of()), points);
            Goal goal = goals.get(s.getId());
            Double expected = goal == null ? null
                    : GoalPace.expectedToday(goal.setOn(), goal.baseline(), examDate, goal.target(), today);
            boolean behind = goal != null
                    && GoalPace.behind(goal.setOn(), goal.baseline(), examDate, goal.target(), p.grade(), today);
            rows.add(new ClassGoalRowDto(s.getId(), s.getFullName(), p.grade(), p.low(), p.high(),
                    goal == null ? null : goal.target(), expected, behind));
        }
        return rows;
    }
}
