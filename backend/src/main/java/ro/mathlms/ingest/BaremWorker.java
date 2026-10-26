package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mathlms.aigrading.AiGradingProperties;

/**
 * Reads queued barem photos in the background, one at a time (one strong-model call of up to a few minutes), and fails
 * reads a restart interrupted. Does nothing while the feature is off or {@code app.ai.worker.enabled=false} (tests
 * drive the service directly).
 */
@Component
public class BaremWorker {

    private static final Logger log = LoggerFactory.getLogger(BaremWorker.class);
    static final int MAX_PER_TICK = 3;

    private final BaremService service;
    private final boolean enabled;

    public BaremWorker(BaremService service, AiGradingProperties properties,
                       @Value("${app.ai.worker.enabled:true}") boolean workerEnabled) {
        this.service = service;
        this.enabled = properties.enabled() && workerEnabled;
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.interval-ms:3000}",
            initialDelayString = "${app.ai.worker.initial-delay-ms:20000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            int recovered = service.recoverStuck();
            if (recovered > 0) {
                log.warn("{} barem read(s) were interrupted and marked failed", recovered);
            }
            for (int i = 0; i < MAX_PER_TICK && service.processNext(); i++) {
                // one read per iteration; stops as soon as the queue is empty
            }
        } catch (Exception e) {
            log.error("Barem read tick failed", e); // never let a bad tick kill the scheduler thread
        }
    }
}
