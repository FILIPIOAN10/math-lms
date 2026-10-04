package ro.mathlms.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);

    @Test
    void gaugesFollowTheTableCountsOnEveryRead() {
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L);
        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(0L);
        new OutboxMetrics(registry, repository);

        assertThat(registry.get("outbox_events").tag("status", "pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("outbox_events").tag("status", "dead").gauge().value()).isZero();

        when(repository.countByStatus(OutboxStatus.DEAD)).thenReturn(2L);
        assertThat(registry.get("outbox_events").tag("status", "dead").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void aDatabaseErrorBecomesNaNInsteadOfBreakingTheScrape() {
        when(repository.countByStatus(OutboxStatus.PENDING)).thenThrow(new IllegalStateException("db down"));
        when(repository.countByStatus(OutboxStatus.DEAD)).thenThrow(new IllegalStateException("db down"));
        new OutboxMetrics(registry, repository);

        assertThat(registry.get("outbox_events").tag("status", "dead").gauge().value()).isNaN();
    }
}
