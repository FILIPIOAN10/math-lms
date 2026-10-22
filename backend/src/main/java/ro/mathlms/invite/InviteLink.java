package ro.mathlms.invite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A stored invite link (T1) that says where the newcomer belongs: a class to join, or a student to be the parent of.
 * Capped ({@code maxUses}), expiring and revocable, so a forwarded link cannot let an unlimited number of strangers in.
 * The use counter is claimed atomically by {@link InviteLinkRepository#claimUse}; this entity only answers why a link
 * can no longer be used.
 */
@Entity
@Table(name = "invite_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink {

    /** How long a link stays usable. */
    public static final Duration TTL = Duration.ofDays(7);
    /** What a class link allows unless the teacher picks another number. */
    public static final int DEFAULT_CLASS_USES = 30;
    /** The most uses one link may allow (also a CHECK in the database). */
    public static final int MAX_USES = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The secret in the URL: random, URL-safe, never a JWT (so it is told apart from a role invite by shape). */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InviteLinkKind kind;

    /** The class a CLASS link enrols into; null for a PARENT link. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_class_id")
    private SchoolClass schoolClass;

    /** The student a PARENT link links to; null for a CLASS link. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(nullable = false)
    private int uses;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private InviteLink(String code, InviteLinkKind kind, int maxUses, Instant now) {
        this.code = Objects.requireNonNull(code, "code");
        this.kind = kind;
        if (maxUses < 1 || maxUses > MAX_USES) {
            throw new IllegalArgumentException("maxUses must be between 1 and " + MAX_USES + ", was " + maxUses);
        }
        this.maxUses = maxUses;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.expiresAt = now.plus(TTL);
    }

    /** A link that enrols up to {@code maxUses} students in {@code schoolClass}. */
    public static InviteLink forClass(String code, SchoolClass schoolClass, int maxUses, Instant now) {
        InviteLink link = new InviteLink(code, InviteLinkKind.CLASS, maxUses, now);
        link.schoolClass = Objects.requireNonNull(schoolClass, "schoolClass");
        return link;
    }
    public static InviteLink forAdmin(String code, int maxUses, Instant now) {
        InviteLink link = new InviteLink();
        link.code = code;
        link.kind = InviteLinkKind.ADMIN;
        link.maxUses = maxUses;
        link.uses = 0;
        link.createdAt = now;
        link.expiresAt = now.plus(java.time.Duration.ofDays(7));
        return link;
    }

    /** A single-use link for the parent of {@code student}. */
    public static InviteLink forParentOf(String code, User student, Instant now) {
        InviteLink link = new InviteLink(code, InviteLinkKind.PARENT, 1, now);
        link.student = Objects.requireNonNull(student, "student");
        return link;
    }

    /** Stops the link; accounts that already registered through it still finish joining. Idempotent. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now, "now");
        }
    }

    /** Why nobody new may join through this link, in words for the person holding it; empty while it is usable. */
    public Optional<String> unavailableReason(Instant now) {
        if (revokedAt != null) {
            return Optional.of("Linkul de invitație a fost oprit de profesor. Cere-i unul nou.");
        }
        if (!now.isBefore(expiresAt)) {
            return Optional.of("Linkul de invitație a expirat. Cere profesorului unul nou.");
        }
        if (uses >= maxUses) {
            return Optional.of("Linkul de invitație a fost folosit deja de numărul maxim de persoane. "
                    + "Cere profesorului unul nou.");
        }
        return Optional.empty();
    }
}
