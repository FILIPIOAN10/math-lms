package ro.mathlms.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Drains one batch of due outbox events in its own transaction (the dispatcher's {@code @Scheduled} tick is not
 * transactional; tests call this directly). A handler failure is contained to its own row — attempt counted,
 * backed off or dead-lettered — and never rolls back the siblings in the batch.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository repository;
    private final OutboxHandlerRegistry registry;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public OutboxProcessor(OutboxEventRepository repository, OutboxHandlerRegistry registry,
                           @Value("${app.outbox.batch-size:20}") int batchSize,
                           @Value("${app.outbox.max-attempts:8}") int maxAttempts,
                           @Value("${app.outbox.base-backoff-seconds:30}") long baseBackoffSeconds) {
        this.repository = repository;
        this.registry = registry;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofSeconds(baseBackoffSeconds);
    }

    /** @return how many events were claimed this run (0 when the queue is drained) */
    @Transactional
    public int processBatch() {
        List<OutboxEvent> batch = repository.claimBatch(batchSize);
        for (OutboxEvent event : batch) {
            try {
                registry.handlerFor(event.getEventType()).handle(event.getPayload());
                event.markDone();
            } catch (Exception e) {
                event.recordFailure(e, maxAttempts, baseBackoff);
                if (event.getStatus() == OutboxStatus.DEAD) {
                    log.error("Outbox event {} ({}) dead-lettered after {} attempts: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), e.toString());
                } else {
                    log.warn("Outbox event {} ({}) failed attempt {}, will retry: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), e.toString());
                }
            }
        }
        return batch.size();
    }
}
