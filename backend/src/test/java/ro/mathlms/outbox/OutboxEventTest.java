package ro.mathlms.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final Duration BASE = Duration.ofSeconds(30);

    @Test
    void startsPendingAndDue() {
        OutboxEvent event = OutboxEvent.of("T", "{}");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void markDoneFinishesTheEvent() {
        OutboxEvent event = OutboxEvent.of("T", "{}");

        event.markDone();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    void aFailureBacksOffExponentially() {
        OutboxEvent event = OutboxEvent.of("T", "{}");

        event.recordFailure(new IllegalStateException("smtp down"), 8, BASE);
        Instant afterFirst = event.getNextAttemptAt();
        event.recordFailure(new IllegalStateException("smtp down"), 8, BASE);
        Instant afterSecond = event.getNextAttemptAt();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLastError()).contains("smtp down");
        // 30s then 60s from "now": the second delay is clearly longer than the first
        assertThat(Duration.between(Instant.now(), afterFirst).getSeconds()).isBetween(25L, 31L);
        assertThat(Duration.between(Instant.now(), afterSecond).getSeconds()).isBetween(55L, 61L);
    }

    @Test
    void backoffIsCapped() {
        OutboxEvent event = OutboxEvent.of("T", "{}");
        for (int i = 0; i < 12; i++) {
            event.recordFailure(new RuntimeException("x"), 100, BASE);
        }

        assertThat(Duration.between(Instant.now(), event.getNextAttemptAt()).getSeconds()).isLessThanOrEqualTo(3600L);
    }

    @Test
    void theLastAllowedFailureDeadLettersTheEvent() {
        OutboxEvent event = OutboxEvent.of("T", "{}");

        event.recordFailure(new RuntimeException("1"), 2, BASE);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        event.recordFailure(new RuntimeException("2"), 2, BASE);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    void aHugeErrorMessageIsTruncated() {
        OutboxEvent event = OutboxEvent.of("T", "{}");

        event.recordFailure(new RuntimeException("x".repeat(10_000)), 8, BASE);

        assertThat(event.getLastError().length()).isLessThanOrEqualTo(4000);
    }
}
