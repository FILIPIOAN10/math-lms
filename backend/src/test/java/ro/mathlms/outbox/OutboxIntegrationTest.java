package ro.mathlms.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import ro.mathlms.TestcontainersConfiguration;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The outbox on real Postgres: atomic publish, delivery, retry -> dead letter, and the purge of finished rows. */
@Import({TestcontainersConfiguration.class, OutboxIntegrationTest.TestHandlers.class})
@SpringBootTest(properties = {"app.outbox.max-attempts=2", "app.outbox.base-backoff-seconds=0"})
class OutboxIntegrationTest {

    static final AtomicInteger OK_CALLS = new AtomicInteger();

    @TestConfiguration
    static class TestHandlers {
        @Bean
        OutboxHandler okHandler() {
            return new OutboxHandler() {
                public String eventType() { return "IT_OK"; }
                public void handle(String payload) { OK_CALLS.incrementAndGet(); }
            };
        }

        @Bean
        OutboxHandler failingHandler() {
            return new OutboxHandler() {
                public String eventType() { return "IT_FAIL"; }
                public void handle(String payload) { throw new IllegalStateException("boom"); }
            };
        }
    }

    @Autowired private OutboxEventPublisher publisher;
    @Autowired private OutboxProcessor processor;
    @Autowired private OutboxPurger purger;
    @Autowired private OutboxEventRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void publishingOutsideATransactionFailsLoudlyAndWritesNothing() {
        long before = repository.count();

        assertThatThrownBy(() -> publisher.publish("IT_OK", "x")).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void aRolledBackTransactionLeavesNoOutboxRow() {
        long before = repository.countByStatus(OutboxStatus.PENDING);

        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            publisher.publish("IT_OK", "x");
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.countByStatus(OutboxStatus.PENDING)).isEqualTo(before);
    }

    @Test
    void aCommittedEventIsDeliveredOnceAndMarkedDone() {
        OK_CALLS.set(0);
        Long id = tx().execute(status -> publisher.publish("IT_OK", "hello").getId());

        drain();

        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(OK_CALLS.get()).isEqualTo(1);
    }

    @Test
    void aFailingHandlerIsRetriedThenDeadLettered() {
        Long id = tx().execute(status -> publisher.publish("IT_FAIL", "x").getId());

        processor.processBatch(); // attempt 1: failed, rescheduled (backoff 0 -> due again at once)
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
        processor.processBatch(); // attempt 2 = max-attempts -> dead letter

        OutboxEvent dead = repository.findById(id).orElseThrow();
        assertThat(dead.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(dead.getAttempts()).isEqualTo(2);
        assertThat(dead.getLastError()).contains("boom");
    }

    @Test
    void purgeRemovesFinishedRowsButKeepsDeadOnes() {
        Long done = tx().execute(status -> publisher.publish("IT_OK", "old").getId());
        Long dead = tx().execute(status -> publisher.publish("IT_FAIL", "old").getId());
        drain(); // done -> DONE; the failing one retries to max-attempts -> DEAD

        // "older than an hour from now" = every DONE row (the retention itself is only a config value)
        tx().executeWithoutResult(status -> repository.deleteDoneBefore(Instant.now().plusSeconds(3600)));

        assertThat(repository.findById(done)).isEmpty();
        assertThat(repository.findById(dead)).isPresent();
        assertThat(purger.purge()).isZero(); // nothing finished is old enough under the real 14-day retention
    }

    private void drain() {
        while (processor.processBatch() > 0) {
            // keep going until nothing is due
        }
    }
}
