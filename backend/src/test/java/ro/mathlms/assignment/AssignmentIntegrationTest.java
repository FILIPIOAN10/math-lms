package ro.mathlms.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentDtos.StudentStatusDto;
import ro.mathlms.assignment.AssignmentProgress.State;
import ro.mathlms.auth.EmailService;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.outbox.OutboxEvent;
import ro.mathlms.outbox.OutboxEventRepository;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxProcessor;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * E7 end to end on real Postgres: a teacher assigns a quiz to a class, the status follows what the students actually
 * do, and the "due soon" reminder goes once, to the students who have not handed in, through the outbox.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.notifications.assignment-reminder.enabled=true",
        "app.assignments.reminder-initial-delay-ms=3600000", "app.assignments.reminder-interval-ms=3600000", // the test ticks the job itself
        "app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000",
        "app.outbox.base-backoff-seconds=0"})
class AssignmentIntegrationTest {

    @MockitoBean private EmailService emailService;

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AssignmentService assignmentService;
    @Autowired private AssignmentReminderJob reminderJob;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private OutboxProcessor processor;
    @Autowired private AssignmentRepository assignmentRepository;

    private SchoolClass schoolClass;
    private Quiz quiz;
    private User ana;   // will not hand in
    private User bob;   // will hand in
    private User cris;  // in another class

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa " + stamp, null));
        SchoolClass elsewhere = schoolClassRepository.save(new SchoolClass("Alta " + stamp, null));
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        bob = userRepository.save(new User("bob." + stamp + "@scoala.ro", "Bob Ionescu", Role.STUDENT));
        cris = userRepository.save(new User("cris." + stamp + "@scoala.ro", "Cris Rus", Role.STUDENT));
        enrollmentRepository.save(new Enrollment(ana, schoolClass));
        enrollmentRepository.save(new Enrollment(bob, schoolClass));
        enrollmentRepository.save(new Enrollment(cris, elsewhere));
        Quiz q = new Quiz("Tema " + stamp, null);
        q.publish();
        quiz = quizRepository.save(q);
    }

    private List<OutboxEvent> reminderEvents(Long assignmentId) {
        return outboxRepository.findByEventTypeOrderById(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL).stream()
                .filter(e -> e.getPayload().contains("\"assignmentId\":" + assignmentId + ","))
                .toList();
    }

    @Test
    void theStatusFollowsWhatStudentsDoAndTheStudentSeesTheirOwnList() {
        Assignment assignment = assignmentService.create(quiz.getId(), schoolClass.getId(), Instant.now().plus(Duration.ofDays(2)));

        // nobody started: both enrolled students are NOT_STARTED, the student of another class is not even listed
        assertThat(assignmentService.status(assignment.getId())).extracting(StudentStatusDto::state)
                .containsOnly(State.NOT_STARTED).hasSize(2);
        assertThat(assignmentService.myAssignments(cris.getEmail())).isEmpty();
        assertThat(assignmentService.myAssignments(ana.getEmail())).singleElement()
                .satisfies(a -> assertThat(a.state()).isEqualTo(State.NOT_STARTED));

        // bob hands the test in; ana only starts a PRACTICE (which must not count)
        StartedAttemptDto bobTest = attemptService.startAttempt(quiz.getId(), bob.getEmail());
        attemptService.submit(bobTest.attemptId(), bob.getEmail());

        List<StudentStatusDto> status = assignmentService.status(assignment.getId());
        assertThat(status).filteredOn(s -> s.studentId().equals(bob.getId())).singleElement().satisfies(s -> {
            assertThat(s.state()).isIn(State.SUBMITTED, State.GRADED);
            assertThat(s.late()).isFalse();
            assertThat(s.attemptId()).isEqualTo(bobTest.attemptId());
        });
        assertThat(status).filteredOn(s -> s.studentId().equals(ana.getId())).singleElement()
                .satisfies(s -> assertThat(s.state()).isEqualTo(State.NOT_STARTED));

        AssignmentSummaryDto summary = assignmentService.summaryOf(assignment.getId());
        assertThat(summary.enrolled()).isEqualTo(2);
        assertThat(summary.done()).isEqualTo(1);
        assertThat(summary.late()).isZero();
        assertThat(summary.notStarted()).isEqualTo(1);

        StudentAssignmentDto bobsView = assignmentService.myAssignments(bob.getEmail()).get(0);
        assertThat(bobsView.state()).isIn(State.SUBMITTED, State.GRADED);
        assertThat(bobsView.attemptId()).isEqualTo(bobTest.attemptId());
    }

    @Test
    void theReminderGoesOnceToTheStudentsWhoHaveNotHandedIn() {
        Assignment assignment = assignmentService.create(quiz.getId(), schoolClass.getId(), Instant.now().plus(Duration.ofHours(10)));
        StartedAttemptDto bobTest = attemptService.startAttempt(quiz.getId(), bob.getEmail());
        attemptService.submit(bobTest.attemptId(), bob.getEmail());

        reminderJob.tick();

        // one event, for ana only (bob handed in; cris is in another class)
        assertThat(reminderEvents(assignment.getId())).hasSize(1);
        while (processor.processBatch() > 0) {
            // drain the outbox
        }
        verify(emailService, times(1)).sendAssignmentReminder(eq(ana.getEmail()), eq("Ana Pop"), anyString(), any(), eq(quiz.getId()));
        verify(emailService, never()).sendAssignmentReminder(eq(bob.getEmail()), anyString(), anyString(), any(), anyLong());
        verify(emailService, never()).sendAssignmentReminder(eq(cris.getEmail()), anyString(), anyString(), any(), anyLong());

        // a second tick finds nothing left to remind about
        reminderJob.tick();
        assertThat(reminderEvents(assignment.getId())).hasSize(1);
    }

    @Test
    void anAssignmentDueFarAwayGetsNoReminderYetAndMovingItRearmsTheReminder() {
        Assignment assignment = assignmentService.create(quiz.getId(), schoolClass.getId(), Instant.now().plus(Duration.ofDays(5)));

        reminderJob.tick();
        assertThat(reminderEvents(assignment.getId())).isEmpty();      // outside the 24 h window

        assignmentService.reschedule(assignment.getId(), Instant.now().plus(Duration.ofHours(12)));
        reminderJob.tick();
        assertThat(reminderEvents(assignment.getId())).hasSize(2);     // now inside it: ana and bob, neither handed in
    }

    @Test
    void aLateHandInIsFlagged() {
        // an assignment created in the past with a deadline just passed: build it directly, then hand in afterwards
        Instant created = Instant.now().minus(Duration.ofDays(1));
        // (the service refuses past deadlines, so this row goes in through the repository)
        Assignment past = assignmentRepository.save(new Assignment(quiz, schoolClass, Instant.now().minusSeconds(60), created));

        StartedAttemptDto test = attemptService.startAttempt(quiz.getId(), ana.getEmail());
        attemptService.submit(test.attemptId(), ana.getEmail());

        assertThat(assignmentService.status(past.getId())).filteredOn(s -> s.studentId().equals(ana.getId()))
                .singleElement().satisfies(s -> assertThat(s.late()).isTrue());
        assertThat(assignmentService.myAssignments(ana.getEmail()).get(0).late()).isTrue();
    }
}
