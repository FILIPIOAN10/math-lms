package ro.mathlms.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Deletes idempotency keys older than the retention: a retry comes within minutes, so a week is plenty. */
@Component
public class IdempotencyPurger {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPurger.class);

    private final IdempotencyKeyRepository repository;
    private final Duration retention;

    public IdempotencyPurger(IdempotencyKeyRepository repository,
                             @Value("${app.idempotency.retention-days:7}") long retentionDays) {
        this.repository = repository;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${app.idempotency.purge-cron:0 45 3 * * *}")
    @Transactional
    public int purge() {
        int deleted = repository.deleteCreatedBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Purged {} idempotency key(s)", deleted);
        }
        return deleted;
    }
}
