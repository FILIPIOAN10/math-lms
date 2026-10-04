package ro.mathlms.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Deletes DONE outbox rows older than the retention (they are only history). DEAD rows stay for a human. */
@Component
public class OutboxPurger {

    private static final Logger log = LoggerFactory.getLogger(OutboxPurger.class);

    private final OutboxEventRepository repository;
    private final boolean enabled;
    private final Duration retention;

    public OutboxPurger(OutboxEventRepository repository,
                        @Value("${app.outbox.enabled:true}") boolean enabled,
                        @Value("${app.outbox.done-retention-days:14}") long retentionDays) {
        this.repository = repository;
        this.enabled = enabled;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${app.outbox.purge-cron:0 30 3 * * *}")
    public void purgeOnSchedule() {
        if (enabled) {
            purge();
        }
    }

    @Transactional
    public int purge() {
        int deleted = repository.deleteDoneBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Purged {} finished outbox events", deleted);
        }
        return deleted;
    }
}
