package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads queued AI imports in the background, a few per tick, one at a time (each read is one provider call of up to a
 * minute). Also fails reads a restart interrupted and deletes page files past their keep date. Does nothing while the
 * feature is off or {@code app.ai.worker.enabled=false} (tests drive the service directly).
 */
@Component
public class AiImportWorker {

    private static final Logger log = LoggerFactory.getLogger(AiImportWorker.class);
    static final int MAX_PER_TICK = 5;

    private final AiImportService service;
    private final boolean enabled;

    public AiImportWorker(AiImportService service, AiImportProperties properties,
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
                log.warn("{} AI import(s) were interrupted and marked failed", recovered);
            }
            for (int i = 0; i < MAX_PER_TICK && service.processNext(); i++) {
                // one read per iteration; stops as soon as the queue is empty
            }
        } catch (Exception e) {
            log.error("AI import tick failed", e); // never let a bad tick kill the scheduler thread
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.cleanup-interval-ms:3600000}",
            initialDelayString = "${app.ai.worker.initial-delay-ms:20000}")
    public void cleanup() {
        if (!enabled) {
            return;
        }
        try {
            int forgotten = service.forgetExpiredPages();
            if (forgotten > 0) {
                log.info("Deleted the page photos of {} old AI import(s)", forgotten);
            }
        } catch (Exception e) {
            log.error("AI import cleanup failed", e);
        }
    }
}
