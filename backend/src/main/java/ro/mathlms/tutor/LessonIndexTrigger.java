package ro.mathlms.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Plan step 6: the tutor answers from the lessons the teacher approved and showed. After an approval, a correction or a
 * show / hide is committed, the tutor service re-embeds the approved and shown lessons (POST /lesson-index; only what
 * changed is paid for) - in the background, so the teacher never waits for it, and a tutor that is off or down only
 * leaves the index as it was (logged; the next change catches up).
 */
@Component
public class LessonIndexTrigger {

    private static final Logger log = LoggerFactory.getLogger(LessonIndexTrigger.class);

    private final TutorProperties properties;
    private final TutorClient client;
    private final TaskExecutor executor;

    public LessonIndexTrigger(TutorProperties properties, TutorClient client,
                              @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.properties = properties;
        this.client = client;
        this.executor = executor;
    }

    /** Call inside the transaction that changed a lesson: the index is refreshed once it is committed. */
    public void lessonsChanged() {
        if (!properties.enabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(LessonIndexTrigger.this::refresh);
                }
            });
        } else {
            executor.execute(this::refresh);
        }
    }

    private void refresh() {
        try {
            TutorClient.IndexReport report = client.indexLessons();
            log.info("Lesson index: {} new, {} unchanged, {} removed", report.inserted(), report.unchanged(),
                    report.removed());
        } catch (RuntimeException e) {
            log.warn("Lesson index not refreshed: {}", e.getMessage());
        }
    }
}
