package ro.mathlms.common;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The loop every AI queue shares: recover first, then a few jobs, and a failure never escapes the tick. */
class AiQueueWorkerTest {

    private final AiQueue queue = mock(AiQueue.class);

    private AiQueueWorker worker(boolean enabled, int maxPerTick) {
        return new AiQueueWorker(queue, enabled, maxPerTick, "test job(s)") {
        };
    }

    @Test
    void recoversFirstThenRunsAtMostMaxPerTick() {
        when(queue.processNext()).thenReturn(true);

        worker(true, 3).drain();

        var order = inOrder(queue);
        order.verify(queue).recoverStuck();
        order.verify(queue, times(3)).processNext();
    }

    @Test
    void stopsAsSoonAsTheQueueIsEmpty() {
        when(queue.processNext()).thenReturn(true, false);

        worker(true, 5).drain();

        verify(queue, times(2)).processNext();
    }

    @Test
    void doesNothingWhileOff() {
        worker(false, 5).drain();

        verifyNoInteractions(queue);
    }

    @Test
    void aFailingTickIsSwallowed() {
        when(queue.recoverStuck()).thenThrow(new IllegalStateException("db down"));

        worker(true, 5).drain(); // no exception: the scheduler thread must survive

        verify(queue, times(0)).processNext();
    }
}
