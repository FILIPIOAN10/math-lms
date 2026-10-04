package ro.mathlms.quiz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hands in timed attempts whose time ran out, so a student who closed the tab (or lost the connection) still gets
 * what they had saved graded, instead of an attempt stuck IN_PROGRESS forever. Each attempt is its own transaction
 * (see {@link QuizAttemptService#autoSubmitIfOverdue}); one failing attempt never stops the others.
 */
@Component
public class AttemptExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryJob.class);

    private final QuizAttemptService service;
    private final boolean enabled;

    public AttemptExpiryJob(QuizAttemptService service, @Value("${app.quiz.expiry-job.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.quiz.expiry-job.interval-ms:30000}",
            initialDelayString = "${app.quiz.expiry-job.initial-delay-ms:20000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            for (Long attemptId : service.findOverdueAttemptIds()) {
                try {
                    service.autoSubmitIfOverdue(attemptId);
                } catch (Exception e) {
                    log.error("Auto-submit of attempt {} failed", attemptId, e);
                }
            }
        } catch (Exception e) {
            log.error("Quiz expiry tick failed", e); // never let a bad tick kill the scheduler thread
        }
    }
}
