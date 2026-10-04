package ro.mathlms.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * One durable, retryable side effect owed because a transaction committed. Written by
 * {@link OutboxEventPublisher} inside the business transaction, drained by {@link OutboxProcessor}.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 4000;
    /** A retry is never delayed longer than this, however many attempts have failed. */
    private static final long MAX_BACKOFF_SECONDS = 3600;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OutboxEvent of(String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.payload = payload;
        return event;
    }

    /** The side effect completed. */
    public void markDone() {
        this.status = OutboxStatus.DONE;
    }

    /**
     * A dispatch attempt failed: count it and either schedule an exponentially backed-off retry
     * ({@code base * 2^(attempt-1)}, capped) or, once {@code maxAttempts} is reached, dead-letter the event.
     */
    public void recordFailure(Throwable error, int maxAttempts, Duration baseBackoff) {
        this.attempts += 1;
        String message = String.valueOf(error);
        this.lastError = message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.DEAD;
        } else {
            long backoff = Math.min(baseBackoff.getSeconds() * (1L << (this.attempts - 1)), MAX_BACKOFF_SECONDS);
            this.nextAttemptAt = Instant.now().plusSeconds(backoff);
        }
    }
}
