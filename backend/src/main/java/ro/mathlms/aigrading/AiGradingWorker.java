package ro.mathlms.aigrading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mathlms.common.AiQueueWorker;

/**
 * Asks the AI for the queued grade proposals in the background, a few per tick, one at a time (each a strong-model
 * call of up to a few minutes), and fails calls a restart interrupted. See {@link AiQueueWorker}.
 */
@Component
public class AiGradingWorker extends AiQueueWorker {

    static final int MAX_PER_TICK = 5;

    public AiGradingWorker(AiGradingService service, AiGradingProperties properties,
                           @Value("${app.ai.worker.enabled:true}") boolean workerEnabled) {
        super(service, properties.enabled() && workerEnabled, MAX_PER_TICK, "AI grade proposal(s)");
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.interval-ms:3000}",
            initialDelayString = "${app.ai.worker.initial-delay-ms:20000}")
    public void tick() {
        drain();
    }
}
