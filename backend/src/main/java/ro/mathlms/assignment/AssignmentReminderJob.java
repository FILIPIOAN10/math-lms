package ro.mathlms.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Queues the "homework due soon" emails. Off unless {@code app.notifications.assignment-reminder.enabled=true}, so dev
 * and tests never send mail by accident (the same switch pattern as the "result is ready" email). Each assignment is
 * its own transaction; one failing assignment never stops the others.
 */
@Component
public class AssignmentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AssignmentReminderJob.class);

    private final AssignmentService service;
    private final boolean enabled;

    public AssignmentReminderJob(AssignmentService service,
                                 @Value("${app.notifications.assignment-reminder.enabled:false}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.assignments.reminder-interval-ms:900000}",
            initialDelayString = "${app.assignments.reminder-initial-delay-ms:60000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            for (Long assignmentId : service.findDueForReminderIds()) {
                try {
                    service.queueReminders(assignmentId);
                } catch (Exception e) {
                    log.error("Queueing the reminders of assignment {} failed", assignmentId, e);
                }
            }
        } catch (Exception e) {
            log.error("Assignment reminder tick failed", e); // never let a bad tick kill the scheduler thread
        }
    }
}
