package ro.mathlms.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox on a fixed delay and hands each due batch to {@link OutboxProcessor}, draining while
 * batches come back full. Safe with several instances (SKIP LOCKED). {@code app.outbox.enabled=false} turns the
 * poller off — tests drive {@link OutboxProcessor} directly so assertions never race a background tick.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxProcessor processor;
    private final boolean enabled;
    private final int maxBatchesPerTick;

    public OutboxDispatcher(OutboxProcessor processor,
                            @Value("${app.outbox.enabled:true}") boolean enabled,
                            @Value("${app.outbox.max-batches-per-tick:10}") int maxBatchesPerTick) {
        this.processor = processor;
        this.enabled = enabled;
        this.maxBatchesPerTick = maxBatchesPerTick;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}",
            initialDelayString = "${app.outbox.initial-delay-ms:15000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        try {
            for (int i = 0; i < maxBatchesPerTick; i++) {
                if (processor.processBatch() == 0) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Outbox dispatch tick failed", e); // never let a bad tick kill the scheduler thread
        }
    }
}
