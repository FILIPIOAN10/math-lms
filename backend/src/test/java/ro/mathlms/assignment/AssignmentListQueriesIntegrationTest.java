package ro.mathlms.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The homework list (the teacher's Acasă, Teme, the mastery page) on real Postgres: it asks the database the same few
 * times however many homeworks there are - it used to ask three times more for every one of them (audit M1,
 * 2026-11-10) - and its numbers stay right: exceptions, catch-up students, who handed in.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=ro.mathlms.assignment.CountingStatementInspector",
        "app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class AssignmentListQueriesIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AssignmentService assignmentService;
    @Autowired private QuizAttemptService attemptService;

    private SchoolClass schoolClass;
    private User ana; // works to her own deadline, never hands in
    private User bob; // hands every whole-class homework in

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa " + stamp, null));
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        bob = userRepository.save(new User("bob." + stamp + "@scoala.ro", "Bob Ionescu", Role.STUDENT));
        enrollmentRepository.save(new Enrollment(ana, schoolClass));
        enrollmentRepository.save(new Enrollment(bob, schoolClass));
    }

    private Quiz test() {
        Quiz q = new Quiz("Tema " + System.nanoTime(), null);
        q.assignToClass(schoolClass);
        q.publish();
        return quizRepository.save(q);
    }

    /** Whole-class homework: Ana has a deadline of her own, Bob hands it in. */
    private void homework() {
        Quiz quiz = test();
        Assignment assignment = assignmentService.create(quiz.getId(), schoolClass.getId(),
                Instant.now().plus(Duration.ofDays(3)));
        assignmentService.setOverride(assignment.getId(), ana.getId(), Instant.now().plus(Duration.ofDays(5)), false);
        Long attempt = attemptService.startAttempt(quiz.getId(), bob.getEmail()).attemptId();
        attemptService.submit(attempt, bob.getEmail());
    }

    /** Catch-up homework for Ana only. */
    private void catchUp() {
        assignmentService.createForStudents(test().getId(), schoolClass.getId(), List.of(ana.getId()),
                Instant.now().plus(Duration.ofDays(3)));
    }

    private static int statementsOf(Runnable call) {
        CountingStatementInspector.reset();
        call.run();
        return CountingStatementInspector.count();
    }

    private List<AssignmentSummaryDto> ofThisClass() {
        return assignmentService.list().stream().filter(s -> s.schoolClassId().equals(schoolClass.getId())).toList();
    }

    @Test
    void theListAsksTheDatabaseTheSameFewTimesHoweverManyHomeworksThereAre() {
        homework();
        catchUp();
        int few = statementsOf(assignmentService::list);

        for (int i = 0; i < 5; i++) {
            homework();
            catchUp();
        }
        int many = statementsOf(assignmentService::list);

        assertThat(many).isEqualTo(few);
        assertThat(few).isLessThanOrEqualTo(5);
    }

    @Test
    void theBatchedSummariesKeepTheirNumbers() {
        homework();
        catchUp();

        List<AssignmentSummaryDto> summaries = ofThisClass();

        assertThat(summaries).hasSize(2);
        AssignmentSummaryDto whole = summaries.stream().filter(s -> s.forStudents() == 0).findFirst().orElseThrow();
        assertThat(whole.enrolled()).isEqualTo(2);
        assertThat(whole.done()).isEqualTo(1);       // Bob
        assertThat(whole.notStarted()).isEqualTo(1); // Ana, on her own deadline
        AssignmentSummaryDto chosen = summaries.stream().filter(s -> s.forStudents() == 1).findFirst().orElseThrow();
        assertThat(chosen.enrolled()).isEqualTo(1);  // Ana only
        assertThat(chosen.done()).isZero();
        assertThat(chosen.notStarted()).isEqualTo(1);
    }
}
