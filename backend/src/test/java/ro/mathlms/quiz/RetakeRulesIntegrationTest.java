package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.AssignmentService;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.StudentQuizDtos.OfferedQuizDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Retakes on real Postgres: the limit the teacher set holds on the server, a paper waiting for its grade blocks a retake,
 * homework gives a fresh attempt, and every average counts a student once, with their highest grade.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class RetakeRulesIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizStatsService statsService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private JdbcTemplate jdbc;

    private SchoolClass schoolClass;
    private User ana;
    private User bob;
    private Quiz quiz;
    private QuizItem first;
    private QuizItem second;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa " + stamp, null));
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        bob = userRepository.save(new User("bob." + stamp + "@scoala.ro", "Bob Ionescu", Role.STUDENT));
        enrollmentRepository.save(new Enrollment(ana, schoolClass));
        enrollmentRepository.save(new Enrollment(bob, schoolClass));
    }

    /** A published test of two grile worth 5 p each, for the class. */
    private void grileTest(Integer maxAttempts) {
        Quiz q = new Quiz("Grile " + System.nanoTime(), null);
        q.assignToClass(schoolClass);
        q.limitAttempts(maxAttempts);
        q.allowPractice(true);
        q.publish();
        quiz = quizRepository.save(q);
        first = grila(1);
        second = grila(2);
    }

    private QuizItem grila(int position) {
        QuizItem item = itemRepository.save(new QuizItem(quiz, position, QuizItemType.SINGLE_CHOICE, "g", 5, null));
        optionRepository.save(new QuizOption(item, 0, "bine", true));
        optionRepository.save(new QuizOption(item, 1, "greșit", false));
        return item;
    }

    private Long option(QuizItem item, boolean right) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == right).findFirst().orElseThrow().getId();
    }

    private void take(User student, boolean firstRight, boolean secondRight) {
        Long attempt = attemptService.startAttempt(quiz.getId(), student.getEmail()).attemptId();
        attemptService.saveResponse(attempt, first.getId(), option(first, firstRight), student.getEmail());
        attemptService.saveResponse(attempt, second.getId(), option(second, secondRight), student.getEmail());
        attemptService.submit(attempt, student.getEmail());
    }

    private OfferedQuizDto offered(User student) {
        return attemptService.listOffered(student.getEmail()).stream()
                .filter(q -> q.id().equals(quiz.getId())).findFirst().orElseThrow();
    }

    @Test
    void theLimitHoldsOnTheServerAndPracticeStaysOpen() {
        grileTest(1);

        take(ana, true, true);

        assertThatThrownBy(() -> attemptService.startAttempt(quiz.getId(), ana.getEmail()))
                .isInstanceOf(AttemptLimitException.class)
                .hasMessage("Ai folosit toate încercările la acest test.");
        assertThat(offered(ana).attemptsUsed()).isEqualTo(1);
        assertThat(offered(ana).blockedBy()).isEqualTo(RetakeBlock.NO_ATTEMPTS_LEFT);
        assertThat(attemptService.getPreview(quiz.getId(), ana.getEmail()).blockedBy()).isEqualTo(RetakeBlock.NO_ATTEMPTS_LEFT);
        assertThat(offered(bob).blockedBy()).isNull(); // another student's attempts are not theirs

        StartedAttemptDto practice = attemptService.startAttempt(quiz.getId(), ana.getEmail(), AttemptMode.PRACTICE);
        assertThat(practice.mode()).isEqualTo(AttemptMode.PRACTICE);
    }

    @Test
    void anAttemptInProgressIsResumedNotRefused() {
        grileTest(1);
        Long attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId();

        assertThat(attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId()).isEqualTo(attempt);
    }

    @Test
    void aPaperWaitingForItsGradeBlocksARetakeUntilItIsGraded() {
        grileTest(null);
        QuizItem open = itemRepository.save(new QuizItem(quiz, 3, QuizItemType.OPEN, "d", 5, null));
        Long attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId();
        attemptService.submit(attempt, ana.getEmail());

        assertThatThrownBy(() -> attemptService.startAttempt(quiz.getId(), ana.getEmail()))
                .isInstanceOf(AttemptLimitException.class)
                .hasMessageContaining("se corectează");

        attemptService.gradeOpenResponse(attempt, open.getId(), 3, null);
        attemptService.finalizeGrading(attempt);

        assertThat(attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId()).isNotEqualTo(attempt);
    }

    @Test
    void reopeningAGradeBlocksARetakeAgain() {
        grileTest(null);
        QuizItem open = itemRepository.save(new QuizItem(quiz, 3, QuizItemType.OPEN, "d", 5, null));
        Long attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId();
        attemptService.submit(attempt, ana.getEmail());
        attemptService.gradeOpenResponse(attempt, open.getId(), 3, null);
        attemptService.finalizeGrading(attempt);

        attemptService.reopenGrading(attempt);

        assertThat(offered(ana).blockedBy()).isEqualTo(RetakeBlock.WAITING_FOR_GRADE);
        assertThat(attemptService.getProgress(ana.getEmail())).isEmpty(); // no grade while it is being corrected
    }

    @Test
    void homeworkGivesAFreshAttemptToAStudentWhoUsedThemAll() {
        grileTest(1);
        take(ana, false, false);
        assertThat(offered(ana).blockedBy()).isEqualTo(RetakeBlock.NO_ATTEMPTS_LEFT);

        assignmentService.create(quiz.getId(), schoolClass.getId(), Instant.now().plus(Duration.ofDays(2)));

        assertThat(offered(ana).attemptsUsed()).isZero();
        assertThat(offered(ana).blockedBy()).isNull();
        take(ana, true, true);
        assertThat(offered(ana).blockedBy()).isEqualTo(RetakeBlock.NO_ATTEMPTS_LEFT);
    }

    @Test
    void anOverdueAttemptStaysHandedInWhenNoFreshOneIsLeft() {
        grileTest(1);
        Long attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId();
        jdbc.update("update quiz_attempts set deadline_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(1))), attempt);

        assertThatThrownBy(() -> attemptService.startAttempt(quiz.getId(), ana.getEmail()))
                .isInstanceOf(AttemptLimitException.class);

        assertThat(attemptRepository.findById(attempt).orElseThrow().getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
    }

    @Test
    void theGradingQueueNamesTheClassOfTheTest() {
        grileTest(null);
        itemRepository.save(new QuizItem(quiz, 3, QuizItemType.OPEN, "d", 5, null));
        Long attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId();
        attemptService.submit(attempt, ana.getEmail());

        assertThat(attemptService.listForGrading(QuizAttemptStatus.SUBMITTED))
                .filteredOn(row -> row.attemptId().equals(attempt))
                .singleElement().satisfies(row -> assertThat(row.schoolClassName()).isEqualTo(schoolClass.getName()));
    }

    @Test
    void everyAverageCountsAStudentOnceWithTheirHighestGrade() {
        grileTest(null);
        take(ana, true, false); // 5 of 10
        take(ana, true, true);  // 10 of 10: this one counts
        take(ana, false, false); // 0 of 10: a worse retake changes nothing
        take(bob, true, false); // 5 of 10

        QuizStatsDtos.QuizStatsDto stats = statsService.getStats(quiz.getId());
        assertThat(stats.gradedAttempts()).isEqualTo(2);
        assertThat(stats.averageScore()).isEqualTo(7.5);
        assertThat(stats.items().get(0).correctRate()).isEqualTo(1.0);
        assertThat(stats.items().get(1).correctRate()).isEqualTo(0.5);

        // grades 10 and 5,50
        Double testAverage = quizRepository.listSummaries().stream()
                .filter(r -> r.getQuizId().equals(quiz.getId())).findFirst().orElseThrow().getAverage();
        assertThat(testAverage).isCloseTo(7.75, within(0.001));
        Double classAverage = schoolClassRepository.summaries().stream()
                .filter(r -> r.getClassId().equals(schoolClass.getId())).findFirst().orElseThrow().getAverage();
        assertThat(classAverage).isCloseTo(7.75, within(0.001));
    }
}
