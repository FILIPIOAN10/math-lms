package ro.mathlms.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import ro.mathlms.TestcontainersConfiguration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The @Scheduled jobs must not queue behind each other: a long AI import tick once held back the timed-test
 * auto-submit and the outbox on Spring's default single scheduler thread.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchedulingPoolTest {

    @Autowired private ThreadPoolTaskScheduler scheduler;

    @Test
    void theSchedulerHasSeveralThreads() {
        assertThat(scheduler.getPoolSize()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void aBlockedJobDoesNotHoldBackAnother() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherRan = new CountDownLatch(1);
        scheduler.execute(() -> {
            try {
                release.await(10, TimeUnit.SECONDS); // stands in for a slow AI read
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        scheduler.execute(otherRan::countDown); // stands in for the auto-submit tick

        boolean ran = otherRan.await(5, TimeUnit.SECONDS);
        release.countDown();
        assertThat(ran).isTrue();
    }
}
