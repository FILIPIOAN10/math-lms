package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.auth.EmailService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxPayloadCodec;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssignmentReminderEmailHandlerTest {

    private final Instant now = Instant.parse("2026-10-05T10:00:00Z");
    private final OutboxPayloadCodec codec = new OutboxPayloadCodec();
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final AssignmentReminderEmailHandler handler = new AssignmentReminderEmailHandler(
            codec, assignmentRepository, userRepository, enrollmentRepository, attemptRepository, emailService,
            Clock.fixed(now, ZoneOffset.UTC));

    private final Quiz quiz = published(withId(new Quiz("Simulare EN", null), 10L));
    private final SchoolClass ninth = withId(new SchoolClass("Clasa a 9-a", null), 5L);
    private final User ana = withId(new User("ana@scoala.ro", "Ana Pop", Role.STUDENT), 2L);
    private final Assignment assignment =
            withId(new Assignment(quiz, ninth, now.plus(Duration.ofHours(20)), now.minus(Duration.ofDays(2))), 1L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Quiz published(Quiz quiz) {
        quiz.publish();
        return quiz;
    }

    private String payload() {
        return codec.serialize(new AssignmentReminderPayload(1L, 2L));
    }

    private void stubAll() {
        when(assignmentRepository.findByIdFetched(1L)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(2L, 5L)).thenReturn(true);
        when(attemptRepository.findForAssignment(10L, List.of(2L), assignment.getCreatedAt())).thenReturn(List.of());
    }

    @Test
    void handlesTheReminderEventType() {
        assertThat(handler.eventType()).isEqualTo(OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL);
    }

    @Test
    void emailsTheStudentWhoHasNotHandedIn() {
        stubAll();

        handler.handle(payload());

        verify(emailService).sendAssignmentReminder("ana@scoala.ro", "Ana Pop", "Simulare EN", assignment.getDueAt(), 10L);
    }

    @Test
    void staysSilentWhenTheStudentHandedInInTheMeantime() {
        stubAll();
        QuizAttempt done = new QuizAttempt(quiz, ana, now.minus(Duration.ofDays(1)), AttemptMode.TEST);
        done.submit();
        when(attemptRepository.findForAssignment(10L, List.of(2L), assignment.getCreatedAt())).thenReturn(List.of(done));

        handler.handle(payload());

        verify(emailService, never()).sendAssignmentReminder(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void staysSilentWhenTheDeadlineHasAlreadyPassed() {
        stubAll();
        assignment.reschedule(now.minusSeconds(1));

        handler.handle(payload());

        verify(emailService, never()).sendAssignmentReminder(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void staysSilentForAnErasedStudentAMissingAssignmentOrSomeoneNoLongerInTheClass() {
        stubAll();
        ReflectionTestUtils.setField(ana, "erased", true);
        handler.handle(payload());

        ReflectionTestUtils.setField(ana, "erased", false);
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(2L, 5L)).thenReturn(false);
        handler.handle(payload());

        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(2L, 5L)).thenReturn(true);
        when(assignmentRepository.findByIdFetched(1L)).thenReturn(Optional.empty());
        handler.handle(payload());

        verify(emailService, never()).sendAssignmentReminder(anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void aMailFailureIsRethrownSoTheOutboxRetries() {
        stubAll();
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendAssignmentReminder(eq("ana@scoala.ro"), anyString(), anyString(), any(), anyLong());

        assertThatThrownBy(() -> handler.handle(payload())).hasMessageContaining("smtp down");
    }

    @Test
    void staysSilentWhenTheQuizWasUnpublishedAfterTheReminderWasQueued() {
        stubAll();
        quiz.unpublish();

        handler.handle(payload());

        verifyNoInteractions(emailService);
    }
}
