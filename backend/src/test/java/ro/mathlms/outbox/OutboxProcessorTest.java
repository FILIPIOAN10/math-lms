package ro.mathlms.outbox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxProcessorTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxHandler handler = mock(OutboxHandler.class);
    private final OutboxProcessor processor;

    OutboxProcessorTest() {
        when(handler.eventType()).thenReturn("T");
        processor = new OutboxProcessor(repository, new OutboxHandlerRegistry(List.of(handler)), 20, 2, 0);
    }

    @Test
    void aSuccessfulHandlerMarksTheEventDone() {
        OutboxEvent event = OutboxEvent.of("T", "{\"id\":1}");
        when(repository.claimBatch(anyInt())).thenReturn(List.of(event));

        assertThat(processor.processBatch()).isEqualTo(1);

        verify(handler).handle("{\"id\":1}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void aFailingHandlerOnlyAffectsItsOwnRow() {
        OutboxEvent bad = OutboxEvent.of("T", "bad");
        OutboxEvent good = OutboxEvent.of("T", "good");
        doThrow(new IllegalStateException("smtp down")).when(handler).handle("bad");
        when(repository.claimBatch(anyInt())).thenReturn(List.of(bad, good));

        processor.processBatch();

        assertThat(bad.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(bad.getAttempts()).isEqualTo(1);
        assertThat(good.getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void repeatedFailuresEndInTheDeadLetterState() {
        OutboxEvent bad = OutboxEvent.of("T", "bad");
        doThrow(new IllegalStateException("smtp down")).when(handler).handle("bad");
        when(repository.claimBatch(anyInt())).thenReturn(List.of(bad));

        processor.processBatch();
        processor.processBatch(); // max-attempts is 2

        assertThat(bad.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    void anUnknownEventTypeFailsThatRowInsteadOfTheBatch() {
        OutboxEvent unknown = OutboxEvent.of("MYSTERY", "{}");
        when(repository.claimBatch(anyInt())).thenReturn(List.of(unknown));

        processor.processBatch();

        assertThat(unknown.getAttempts()).isEqualTo(1);
        assertThat(unknown.getLastError()).contains("No OutboxHandler registered");
    }

    @Test
    void anEmptyQueueClaimsNothing() {
        when(repository.claimBatch(anyInt())).thenReturn(List.of());

        assertThat(processor.processBatch()).isZero();
    }
}
