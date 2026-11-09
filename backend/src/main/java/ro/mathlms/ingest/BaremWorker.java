package ro.mathlms.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mathlms.aigrading.AiGradingProperties;
import ro.mathlms.common.AiQueueWorker;

/**
 * Reads queued barem photos in the background, one at a time (one strong-model call of up to a few minutes), and fails
 * reads a restart interrupted. See {@link AiQueueWorker}.
 */
@Component
public class BaremWorker extends AiQueueWorker {

    static final int MAX_PER_TICK = 3;

    public BaremWorker(BaremService service, AiGradingProperties properties,
                       @Value("${app.ai.worker.enabled:true}") boolean workerEnabled) {
        super(service, properties.enabled() && workerEnabled, MAX_PER_TICK, "barem read(s)");
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.interval-ms:3000}",
            initialDelayString = "${app.ai.worker.initial-delay-ms:20000}")
    public void tick() {
        drain();
    }
}
