package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttemptExpiryJobTest {

    private final QuizAttemptService service = mock(QuizAttemptService.class);

    @Test
    void handsInEveryOverdueAttempt() {
        when(service.findOverdueAttemptIds()).thenReturn(List.of(1L, 2L, 3L));

        new AttemptExpiryJob(service, true).tick();

        verify(service).autoSubmitIfOverdue(1L);
        verify(service).autoSubmitIfOverdue(2L);
        verify(service).autoSubmitIfOverdue(3L);
    }

    @Test
    void oneFailingAttemptDoesNotStopTheOthers() {
        when(service.findOverdueAttemptIds()).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("boom")).when(service).autoSubmitIfOverdue(1L);

        new AttemptExpiryJob(service, true).tick();

        verify(service).autoSubmitIfOverdue(2L);
    }

    @Test
    void aFailingLookupDoesNotKillTheTick() {
        when(service.findOverdueAttemptIds()).thenThrow(new IllegalStateException("db down"));

        new AttemptExpiryJob(service, true).tick(); // must not throw: an exception would end the scheduler thread's loop

        verify(service, never()).autoSubmitIfOverdue(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void doesNothingWhenDisabled() {
        new AttemptExpiryJob(service, false).tick();

        verify(service, never()).findOverdueAttemptIds();
    }
}
