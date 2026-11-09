package ro.mathlms.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Works through one {@link AiQueue} in the background: first fails the jobs a restart interrupted, then runs up to
 * {@code maxPerTick} waiting ones, one at a time (each is one provider call of up to a few minutes). Each queue keeps its
 * own {@code @Scheduled} subclass, so the queues run side by side on the scheduler's pool and a long grading call never
 * holds back an import. Does nothing while the feature is off or {@code app.ai.worker.enabled=false} (tests drive the
 * services directly), and a failing tick is logged, never thrown: it must not stop the scheduler thread.
 */
public abstract class AiQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(AiQueueWorker.class);

    private final AiQueue queue;
    private final boolean enabled;
    private final int maxPerTick;
    private final String jobs;

    /** {@code jobs} names them in the log: "AI import(s)", "barem read(s)". */
    protected AiQueueWorker(AiQueue queue, boolean enabled, int maxPerTick, String jobs) {
        this.queue = queue;
        this.enabled = enabled;
        this.maxPerTick = maxPerTick;
        this.jobs = jobs;
    }

    protected boolean isEnabled() {
        return enabled;
    }

    /** One tick: recover, then drain a few. */
    protected void drain() {
        if (!enabled) {
            return;
        }
        try {
            int recovered = queue.recoverStuck();
            if (recovered > 0) {
                log.warn("{} {} were interrupted and marked failed", recovered, jobs);
            }
            for (int i = 0; i < maxPerTick && queue.processNext(); i++) {
                // one job per iteration; stops when the queue is empty or today's limit is reached
            }
        } catch (Exception e) {
            log.error("{} tick failed", jobs, e);
        }
    }
}
