package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mathlms.common.AiQueueWorker;

/**
 * Reads queued AI imports in the background, a few per tick, one at a time (each read is one provider call of up to a
 * minute), and fails reads a restart interrupted (see {@link AiQueueWorker}). Also deletes page files past their keep
 * date.
 */
@Component
public class AiImportWorker extends AiQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(AiImportWorker.class);
    static final int MAX_PER_TICK = 5;

    private final AiImportService service;

    public AiImportWorker(AiImportService service, AiImportProperties properties,
                          @Value("${app.ai.worker.enabled:true}") boolean workerEnabled) {
        super(service, properties.enabled() && workerEnabled, MAX_PER_TICK, "AI import(s)");
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.interval-ms:3000}",
            initialDelayString = "${app.ai.worker.initial-delay-ms:20000}")
    public void tick() {
        drain();
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.cleanup-interval-ms:3600000}",
            initialDelayString = "${app.ai.worker.initial-delay-ms:20000}")
    public void cleanup() {
        if (!isEnabled()) {
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
