package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One more graded attempt for one student, without raising the limit for the whole class (Moodle's user override). */
class AttemptGrantServiceTest {

    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final AttemptGrantRepository grantRepository = mock(AttemptGrantRepository.class);
    private final AuditLog auditLog = mock(AuditLog.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
    private final AttemptGrantService service = new AttemptGrantService(attemptRepository, grantRepository, auditLog, clock);

    private final Quiz quiz = withId(new Quiz("Fracții", null), 10L);
    private final User ana = withId(new User("ana@scoala.ro", "Ana Pop", Role.STUDENT), 7L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void paper() {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, ana, clock.instant(), AttemptMode.TEST), 100L);
        when(attemptRepository.findById(100L)).thenReturn(Optional.of(attempt));
    }

    /** The database's answer to "+1": the student's extra attempts after it (the upsert ran), or empty at the cap. */
    private void grantedTo(Integer extraAfter) {
        when(grantRepository.addOne(eq(10L), eq(7L), eq(clock.instant()), eq(AttemptGrant.MAX_EXTRA)))
                .thenReturn(extraAfter == null ? 0 : 1);
        when(grantRepository.findExtra(10L, 7L)).thenReturn(Optional.ofNullable(extraAfter));
    }

    @Test
    void theFirstGrantAddsOneAttemptForThatStudentAndIsJournalled() {
        quiz.limitAttempts(1);
        paper();
        grantedTo(1);

        assertThat(service.grantOne(100L)).isEqualTo(2);

        verify(auditLog).record(new AuditEvent(AuditAction.ATTEMPT_GRANTED, 100L, "Fracții",
                "încercări 1 → 2", 7L, null));
    }

    @Test
    void aSecondGrantAddsToTheFirst() {
        quiz.limitAttempts(2);
        paper();
        grantedTo(2);

        assertThat(service.grantOne(100L)).isEqualTo(4);
        verify(auditLog).record(new AuditEvent(AuditAction.ATTEMPT_GRANTED, 100L, "Fracții",
                "încercări 3 → 4", 7L, null));
    }

    @Test
    void aTestWithoutALimitNeedsNoGrant() {
        paper();

        assertThatThrownBy(() -> service.grantOne(100L))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("nu are limită");
        verify(grantRepository, never()).addOne(any(), any(), any(), anyInt());
    }

    @Test
    void grantsStopAtTen() {
        quiz.limitAttempts(1);
        paper();
        grantedTo(null); // the upsert's WHERE extra < 10 matched nothing

        assertThatThrownBy(() -> service.grantOne(100L)).isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining(String.valueOf(AttemptGrant.MAX_EXTRA));
        verify(auditLog, never()).record(any());
    }

    @Test
    void anUnknownPaperIsNotFound() {
        when(attemptRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantOne(9L)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void theAllowedCountIsTheLimitPlusTheGrantsAndNoLimitStaysNoLimit() {
        assertThat(AttemptGrant.allowed(1, 2)).isEqualTo(3);
        assertThat(AttemptGrant.allowed(null, 2)).isNull();
    }
}
