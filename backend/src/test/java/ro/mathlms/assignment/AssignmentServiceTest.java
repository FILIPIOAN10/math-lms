package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentDtos.StudentStatusDto;
import ro.mathlms.assignment.AssignmentProgress.State;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.outbox.OutboxEventPublisher;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentServiceTest {

    /** A clock the test moves by hand. */
    private static final class SettableClock extends Clock {
        Instant now = Instant.parse("2026-10-05T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final SchoolClassRepository schoolClassRepository = mock(SchoolClassRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
    private final SettableClock clock = new SettableClock();
    private final AssignmentService service = new AssignmentService(
            assignmentRepository, quizRepository, schoolClassRepository, enrollmentRepository,
            attemptRepository, userRepository, publisher, clock, 24);

    private final SchoolClass ninth = withId(new SchoolClass("Clasa a 9-a", null), 5L);
    private final Quiz quiz = published(withId(new Quiz("Simulare EN", null), 10L));
    private final Instant due = clock.now.plus(Duration.ofDays(3));

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Quiz published(Quiz quiz) {
        quiz.publish();
        return quiz;
    }

    private User student(long id, String name) {
        return withId(new User(name.toLowerCase().replace(' ', '.') + "@scoala.ro", name, Role.STUDENT), id);
    }

    private Assignment assignment(long id) {
        return withId(new Assignment(quiz, ninth, due, clock.now), id);
    }

    private void stubLookups() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.of(ninth));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(i -> withId(i.getArgument(0), 1L));
    }

    // --- creating ---

    @Test
    void createsAnAssignmentStampedWithTheCurrentTime() {
        stubLookups();

        Assignment created = service.create(10L, 5L, due);

        assertThat(created.getQuiz()).isSameAs(quiz);
        assertThat(created.getSchoolClass()).isSameAs(ninth);
        assertThat(created.getDueAt()).isEqualTo(due);
        assertThat(created.getCreatedAt()).isEqualTo(clock.now);
    }

    @Test
    void anUnknownQuizOrClassIsNotFound() {
        when(quizRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, 5L, due)).isInstanceOf(QuizNotFoundException.class);

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, 5L, due)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void aDraftQuizCannotBeAssigned() {
        stubLookups();
        quiz.unpublish();

        assertThatThrownBy(() -> service.create(10L, 5L, due))
                .isInstanceOf(InvalidAssignmentException.class)
                .hasMessageContaining("publicat");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void aQuizMeantForAnotherClassCannotBeAssigned() {
        stubLookups();
        quiz.assignToClass(withId(new SchoolClass("Clasa a 10-a", null), 6L));

        assertThatThrownBy(() -> service.create(10L, 5L, due))
                .isInstanceOf(InvalidAssignmentException.class)
                .hasMessageContaining("altă clasă");
    }

    @Test
    void aQuizMeantForTheSameClassCanBeAssigned() {
        stubLookups();
        quiz.assignToClass(ninth);

        assertThat(service.create(10L, 5L, due)).isNotNull();
    }

    @Test
    void theDeadlineMustBeInTheFuture() {
        stubLookups();

        assertThatThrownBy(() -> service.create(10L, 5L, clock.now))
                .isInstanceOf(InvalidAssignmentException.class)
                .hasMessageContaining("viitor");
        assertThatThrownBy(() -> service.create(10L, 5L, clock.now.minusSeconds(60)))
                .isInstanceOf(InvalidAssignmentException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void theSameQuizCannotBeAssignedTwiceToOneClass() {
        stubLookups();
        when(assignmentRepository.existsByQuizIdAndSchoolClassId(10L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, 5L, due)).isInstanceOf(AssignmentConflictException.class);

        verify(assignmentRepository, never()).save(any());
    }

    // --- changing / removing ---

    @Test
    void theDeadlineCanBeMovedToAnotherFutureMoment() {
        Assignment existing = assignment(1L);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        existing.markReminderSent(clock.now);

        service.reschedule(1L, due.plus(Duration.ofDays(7)));

        assertThat(existing.getDueAt()).isEqualTo(due.plus(Duration.ofDays(7)));
        assertThat(existing.getReminderSentAt()).isNull(); // a new date re-arms the reminder
    }

    @Test
    void aDeadlineInThePastIsRejectedOnUpdateToo() {
        Assignment existing = assignment(1L);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.reschedule(1L, clock.now.minusSeconds(1)))
                .isInstanceOf(InvalidAssignmentException.class);
        assertThat(existing.getDueAt()).isEqualTo(due);
    }

    @Test
    void anUnknownAssignmentIsNotFound() {
        when(assignmentRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reschedule(9L, due)).isInstanceOf(AssignmentNotFoundException.class);
        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    void deletingRemovesIt() {
        Assignment existing = assignment(1L);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(assignmentRepository).delete(existing);
    }

    // --- the teacher's view ---

    private QuizAttempt attemptOf(User who, long id, boolean handedIn, Instant submittedAt) {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, who, clock.now.plusSeconds(30), AttemptMode.TEST), id);
        if (handedIn) {
            attempt.submit();
            ReflectionTestUtils.setField(attempt, "submittedAt", submittedAt);
        }
        return attempt;
    }

    private void stubRoster(Assignment assignment, User... students) {
        when(assignmentRepository.findByIdFetched(1L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdFetchStudent(5L))
                .thenReturn(java.util.Arrays.stream(students).map(s -> new Enrollment(s, ninth)).toList());
    }

    @Test
    void theStatusListsEveryEnrolledStudentWithTheirProgress() {
        Assignment assignment = assignment(1L);
        User ana = student(1L, "Ana Pop");
        User bob = student(2L, "Bob Ionescu");
        User cris = student(3L, "Cris Rus");
        stubRoster(assignment, ana, bob, cris);
        when(attemptRepository.findForAssignment(eq(10L), any(), eq(assignment.getCreatedAt())))
                .thenReturn(List.of(
                        attemptOf(ana, 100L, true, due.minus(Duration.ofHours(2))),     // on time
                        attemptOf(bob, 101L, true, due.plus(Duration.ofHours(1)))));     // late; cris never started

        List<StudentStatusDto> status = service.status(1L);

        assertThat(status).extracting(StudentStatusDto::fullName).containsExactly("Ana Pop", "Bob Ionescu", "Cris Rus");
        assertThat(status).extracting(StudentStatusDto::state)
                .containsExactly(State.SUBMITTED, State.SUBMITTED, State.NOT_STARTED);
        assertThat(status).extracting(StudentStatusDto::late).containsExactly(false, true, false);
    }

    @Test
    void anErasedStudentIsLeftOutOfTheStatus() {
        Assignment assignment = assignment(1L);
        User ana = student(1L, "Ana Pop");
        User gone = student(2L, "Cont sters");
        ReflectionTestUtils.setField(gone, "erased", true);
        stubRoster(assignment, ana, gone);

        assertThat(service.status(1L)).extracting(StudentStatusDto::fullName).containsExactly("Ana Pop");
    }

    @Test
    void theSummaryCountsDoneLateInProgressAndNotStarted() {
        Assignment assignment = assignment(1L);
        User ana = student(1L, "Ana Pop");
        User bob = student(2L, "Bob Ionescu");
        User cris = student(3L, "Cris Rus");
        User dan = student(4L, "Dan Stan");
        when(assignmentRepository.findAllFetched()).thenReturn(List.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdFetchStudent(5L))
                .thenReturn(List.of(ana, bob, cris, dan).stream().map(s -> new Enrollment(s, ninth)).toList());
        when(attemptRepository.findForAssignment(eq(10L), any(), eq(assignment.getCreatedAt())))
                .thenReturn(List.of(
                        attemptOf(ana, 100L, true, due.minus(Duration.ofHours(2))),
                        attemptOf(bob, 101L, true, due.plus(Duration.ofHours(1))),
                        attemptOf(cris, 102L, false, null)));

        AssignmentSummaryDto summary = service.list().get(0);

        assertThat(summary.enrolled()).isEqualTo(4);
        assertThat(summary.done()).isEqualTo(2);
        assertThat(summary.late()).isEqualTo(1);
        assertThat(summary.inProgress()).isEqualTo(1);
        assertThat(summary.notStarted()).isEqualTo(1);
        assertThat(summary.overdue()).isFalse();
    }

    @Test
    void theSummaryFlagsAnAssignmentWhoseDeadlinePassed() {
        Assignment assignment = assignment(1L);
        when(assignmentRepository.findAllFetched()).thenReturn(List.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdFetchStudent(5L)).thenReturn(List.of());
        clock.now = due.plusSeconds(1);

        assertThat(service.list().get(0).overdue()).isTrue();
    }

    // --- the student's view ---

    @Test
    void aStudentSeesTheirClassesAssignmentsNotDoneFirstByDeadline() {
        User ana = student(1L, "Ana Pop");
        Quiz quizB = published(withId(new Quiz("Teza", null), 11L));
        Assignment soon = withId(new Assignment(quiz, ninth, clock.now.plus(Duration.ofDays(1)), clock.now), 1L);
        Assignment later = withId(new Assignment(quizB, ninth, clock.now.plus(Duration.ofDays(5)), clock.now), 2L);
        Quiz quizC = published(withId(new Quiz("Deja facut", null), 12L));
        Assignment finished = withId(new Assignment(quizC, ninth, clock.now.plus(Duration.ofDays(2)), clock.now), 3L);
        when(userRepository.findByEmail(ana.getEmail())).thenReturn(Optional.of(ana));
        when(enrollmentRepository.findClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentRepository.findBySchoolClassIdsFetched(List.of(5L))).thenReturn(List.of(later, finished, soon));
        QuizAttempt doneC = withId(new QuizAttempt(quizC, ana, clock.now.plusSeconds(10), AttemptMode.TEST), 200L);
        doneC.submit();
        when(attemptRepository.findByStudentIdAndQuizIdIn(eq(1L), any())).thenReturn(List.of(doneC));

        List<StudentAssignmentDto> mine = service.myAssignments(ana.getEmail());

        assertThat(mine).extracting(StudentAssignmentDto::quizTitle).containsExactly("Simulare EN", "Teza", "Deja facut");
        assertThat(mine.get(2).state()).isEqualTo(State.SUBMITTED);
        assertThat(mine.get(2).attemptId()).isEqualTo(200L);
        assertThat(mine.get(0).state()).isEqualTo(State.NOT_STARTED);
    }

    @Test
    void anOverdueAssignmentIsFlaggedUntilItIsHandedIn() {
        User ana = student(1L, "Ana Pop");
        Assignment past = withId(new Assignment(quiz, ninth, clock.now.plus(Duration.ofHours(1)), clock.now), 1L);
        when(userRepository.findByEmail(ana.getEmail())).thenReturn(Optional.of(ana));
        when(enrollmentRepository.findClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentRepository.findBySchoolClassIdsFetched(List.of(5L))).thenReturn(List.of(past));
        when(attemptRepository.findByStudentIdAndQuizIdIn(eq(1L), any())).thenReturn(List.of());
        clock.now = clock.now.plus(Duration.ofHours(2));

        assertThat(service.myAssignments(ana.getEmail())).singleElement().satisfies(a -> {
            assertThat(a.overdue()).isTrue();
            assertThat(a.late()).isFalse();
        });
    }

    @Test
    void aStudentWithNoClassesHasNoAssignments() {
        User ana = student(1L, "Ana Pop");
        when(userRepository.findByEmail(ana.getEmail())).thenReturn(Optional.of(ana));
        when(enrollmentRepository.findClassIdsByStudentId(1L)).thenReturn(List.of());

        assertThat(service.myAssignments(ana.getEmail())).isEmpty();
        verify(assignmentRepository, never()).findBySchoolClassIdsFetched(any());
    }

    @Test
    void onlyStudentsHaveAssignments() {
        User teacher = withId(new User("prof@scoala.ro", "Profesor", Role.ADMIN), 9L);
        when(userRepository.findByEmail(teacher.getEmail())).thenReturn(Optional.of(teacher));

        assertThat(service.myAssignments(teacher.getEmail())).isEmpty();
    }

    // --- reminders ---

    @Test
    void remindersAreLookedUpFromNowUntilTheLeadTime() {
        when(assignmentRepository.findIdsDueForReminder(clock.now, clock.now.plus(Duration.ofHours(24))))
                .thenReturn(List.of(1L, 2L));

        assertThat(service.findDueForReminderIds()).containsExactly(1L, 2L);
    }

    @Test
    void aReminderIsQueuedForEachStudentWhoHasNotHandedIn() {
        Assignment assignment = withId(new Assignment(quiz, ninth, clock.now.plus(Duration.ofHours(20)), clock.now), 1L);
        User ana = student(1L, "Ana Pop");      // handed in
        User bob = student(2L, "Bob Ionescu"); // in progress
        User cris = student(3L, "Cris Rus");   // not started
        User gone = student(4L, "Cont sters");
        ReflectionTestUtils.setField(gone, "erased", true);
        when(assignmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdFetched(1L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdFetchStudent(5L))
                .thenReturn(List.of(ana, bob, cris, gone).stream().map(s -> new Enrollment(s, ninth)).toList());
        when(attemptRepository.findForAssignment(eq(10L), any(), eq(assignment.getCreatedAt())))
                .thenReturn(List.of(
                        attemptOf(ana, 100L, true, clock.now.plus(Duration.ofHours(1))),
                        attemptOf(bob, 101L, false, null)));

        service.queueReminders(1L);

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(2)).publish(eq(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL), payloads.capture());
        assertThat(payloads.getAllValues()).containsExactly(
                new AssignmentReminderPayload(1L, 2L), new AssignmentReminderPayload(1L, 3L));
        assertThat(assignment.getReminderSentAt()).isEqualTo(clock.now);
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void aReminderIsOnlyEverQueuedOncePerAssignment() {
        Assignment assignment = withId(new Assignment(quiz, ninth, clock.now.plus(Duration.ofHours(20)), clock.now), 1L);
        assignment.markReminderSent(clock.now.minusSeconds(60));
        when(assignmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));

        service.queueReminders(1L);

        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void noReminderIsQueuedBeforeTheLeadWindowOrAfterTheDeadline() {
        Assignment far = withId(new Assignment(quiz, ninth, clock.now.plus(Duration.ofDays(5)), clock.now), 1L);
        when(assignmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(far));
        service.queueReminders(1L);
        assertThat(far.getReminderSentAt()).isNull();

        Assignment passed = withId(new Assignment(quiz, ninth, clock.now.plusSeconds(10), clock.now.minusSeconds(100)), 2L);
        when(assignmentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(passed));
        clock.now = clock.now.plusSeconds(20);
        service.queueReminders(2L);

        assertThat(passed.getReminderSentAt()).isNull();
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void anAssignmentWithNobodyPendingIsStillMarkedSoItIsNotRetried() {
        Assignment assignment = withId(new Assignment(quiz, ninth, clock.now.plus(Duration.ofHours(5)), clock.now), 1L);
        when(assignmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdFetched(1L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findBySchoolClassIdFetchStudent(5L)).thenReturn(List.of());

        service.queueReminders(1L);

        verify(publisher, never()).publish(any(), any());
        assertThat(assignment.getReminderSentAt()).isEqualTo(clock.now);
    }
}
