package ro.mathlms.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Exposes how many outbox events are waiting and how many were dead-lettered ({@code outbox_events{status=...}}).
 * A DEAD event is a notification that will never be sent without a human — the one silent failure of the outbox,
 * so an alert watches it. The gauge reads the table on each scrape; a database hiccup yields NaN, never an error.
 */
@Component
public class OutboxMetrics {

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository repository) {
        for (OutboxStatus status : new OutboxStatus[]{OutboxStatus.PENDING, OutboxStatus.DEAD}) {
            Gauge.builder("outbox_events", repository, repo -> count(repo, status))
                    .description("Outbox events by status")
                    .tag("status", status.name().toLowerCase())
                    .register(registry);
        }
    }

    private static double count(OutboxEventRepository repository, OutboxStatus status) {
        try {
            return repository.countByStatus(status);
        } catch (RuntimeException e) {
            log.warn("Outbox gauge could not read the {} count: {}", status, e.toString());
            return Double.NaN;
        }
    }
}
