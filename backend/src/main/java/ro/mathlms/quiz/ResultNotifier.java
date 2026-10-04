package ro.mathlms.quiz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.outbox.OutboxEventPublisher;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.quiz.ResultReadyPayload.Audience;

/**
 * Queues the "result is ready" emails when an attempt becomes GRADED: one for the student and, if an admin
 * linked a parent, one for the parent. Must be called inside the grading transaction — the outbox rows then
 * commit atomically with the grade (and vanish with a rollback). Off unless
 * {@code app.notifications.result-ready.enabled=true}, so dev and tests never send mail by accident.
 */
@Component
public class ResultNotifier {

    private final OutboxEventPublisher publisher;
    private final boolean enabled;

    public ResultNotifier(OutboxEventPublisher publisher,
                          @Value("${app.notifications.result-ready.enabled:false}") boolean enabled) {
        this.publisher = publisher;
        this.enabled = enabled;
    }

    public void resultGraded(QuizAttempt attempt) {
        if (!enabled) {
            return;
        }
        publisher.publish(OutboxEventTypes.RESULT_READY_EMAIL, new ResultReadyPayload(attempt.getId(), Audience.STUDENT));
        if (attempt.getStudent().getParent() != null) {
            publisher.publish(OutboxEventTypes.RESULT_READY_EMAIL, new ResultReadyPayload(attempt.getId(), Audience.PARENT));
        }
    }
}
