package ro.mathlms.aigrading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Asks the AI for the queued grade proposals in the background, a few per tick, one at a time (each a strong-model
 * call of up to a few minutes), and fails calls a restart interrupted. Does nothing while the feature is off or
 * {@code app.ai.worker.enabled=false} (tests drive the service directly).
 */
@Component
public class AiGradingWorker {

    private static final Logger log = LoggerFactory.getLogger(AiGradingWorker.class);
    static final int MAX_PER_TICK = 5;

    private final AiGradingService service;
    private final boolean enabled;

    public AiGradingWorker(AiGradingService service, AiGradingProperties properties,
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
                log.warn("{} AI grade proposal(s) were interrupted and marked failed", recovered);
            }
            for (int i = 0; i < MAX_PER_TICK && service.processNext(); i++) {
                // one answer per iteration; stops when the queue is empty or today's limit is reached
            }
        } catch (Exception e) {
            log.error("AI grading tick failed", e); // never let a bad tick kill the scheduler thread
        }
    }
}
