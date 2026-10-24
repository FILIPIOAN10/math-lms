package ro.mathlms.gdpr;

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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One GDPR Art. 15 export of one account: PENDING while the worker builds it, READY with the ZIP until it expires,
 * then EXPIRED (bytes dropped, row kept as the record that the request was answered).
 */
@Entity
@Table(name = "gdpr_exports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GdprExport {

    /** How long a built archive (and its emailed link) stays downloadable. */
    public static final Duration TTL = Duration.ofDays(7);

    public enum Status { PENDING, READY, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** The ZIP; null while PENDING and once EXPIRED. */
    @Column(columnDefinition = "bytea")
    private byte[] archive;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    public GdprExport(Long userId, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = Status.PENDING;
        this.requestedAt = Objects.requireNonNull(now, "now");
    }

    public void ready(byte[] zip, Instant now) {
        this.archive = Objects.requireNonNull(zip, "zip");
        this.status = Status.READY;
        this.readyAt = now;
        this.expiresAt = now.plus(TTL);
    }

    public void downloaded(Instant now) {
        this.downloadedAt = now;
    }

    /** Drops the bytes; the row stays as the record that the request was answered. */
    public void expire() {
        this.archive = null;
        this.status = Status.EXPIRED;
    }

    public boolean isExpired(Instant now) {
        return status == Status.EXPIRED || (expiresAt != null && !now.isBefore(expiresAt));
    }
}
