package ro.mathlms.user;

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

import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /** BCrypt hash; null for Google-only accounts. */
    @Column
    private String password;

    /** Google subject id; null until the account is linked to Google. */
    @Column(name = "google_id")
    private String googleId;

    /** true once the email is confirmed (immediately true for Google logins). */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

    /** Real role, assigned only when an admin approves the account. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role;

    /** Role asked for via the invite link; the real role lives in {@link #role}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", length = 20)
    private Role requestedRole;

    /** The PARENT account this student belongs to; null until an admin links them. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private User parent;

    public User(String email, String fullName, Role role) {
        this.email = requireNonBlank(email, "email");
        this.fullName = requireNonBlank(fullName, "fullName");
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * Creates a local (email/password) account awaiting email verification.
     * The real {@link #role} stays null until an admin approves the requested role.
     */
    public static User registerLocal(String email, String fullName,
                                     String passwordHash, Role requestedRole) {
        User user = new User();
        user.email = requireNonBlank(email, "email");
        user.fullName = requireNonBlank(fullName, "fullName");
        user.password = requireNonBlank(passwordHash, "password");
        user.requestedRole = Objects.requireNonNull(requestedRole, "requestedRole");
        // status defaults to PENDING_VERIFICATION, emailVerified to false, role stays null.
        return user;
    }

    /**
     * Creates an account provisioned from a Google login. Google has already
     * verified the email, and the role comes from the access lists, so the
     * account is active immediately.
     */
    public static User registerGoogle(String googleId, String email, String fullName, Role role) {
        User user = new User();
        user.googleId = requireNonBlank(googleId, "googleId");
        user.email = requireNonBlank(email, "email");
        user.fullName = requireNonBlank(fullName, "fullName");
        user.role = Objects.requireNonNull(role, "role");
        user.emailVerified = true;
        user.status = AccountStatus.ACTIVE;
        return user;
    }

    /**
     * Creates an account from a Google login that arrived via an invite link. Google
     * has verified the email, but the account still needs admin approval, so it starts
     * at {@code PENDING_APPROVAL} with the invited role recorded as {@link #requestedRole}
     * (the real {@link #role} is assigned on approval).
     */
    public static User registerGoogleInvited(String googleId, String email, String fullName,
                                             Role requestedRole) {
        User user = new User();
        user.googleId = requireNonBlank(googleId, "googleId");
        user.email = requireNonBlank(email, "email");
        user.fullName = requireNonBlank(fullName, "fullName");
        user.requestedRole = Objects.requireNonNull(requestedRole, "requestedRole");
        user.emailVerified = true;
        user.status = AccountStatus.PENDING_APPROVAL;
        return user;
    }

    /** Confirms the email and moves the account to approval. */
    public void verifyEmail() {
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException(
                    "verifyEmail requires PENDING_VERIFICATION but was " + status);
        }
        this.emailVerified = true;
        this.status = AccountStatus.PENDING_APPROVAL;
    }

    /** Admin approves the account and assigns its real role. */
    public void approve(Role role) {
        if (status != AccountStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "approve requires PENDING_APPROVAL but was " + status);
        }
        this.role = Objects.requireNonNull(role, "role");
        this.status = AccountStatus.ACTIVE;
    }

    /** Admin rejects a pending account. */
    public void reject() {
        if (status != AccountStatus.PENDING_VERIFICATION
                && status != AccountStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "reject requires a pending account but was " + status);
        }
        this.status = AccountStatus.REJECTED;
    }

    /**
     * Links this student to a parent account. Both must already hold their real role:
     * this account must be a {@link Role#STUDENT} and {@code parent} a {@link Role#PARENT}.
     */
    public void linkParent(User parent) {
        Objects.requireNonNull(parent, "parent");
        if (this.role != Role.STUDENT) {
            throw new IllegalStateException("Only a STUDENT can be linked to a parent, was " + this.role);
        }
        if (parent.role != Role.PARENT) {
            throw new IllegalStateException("Parent account must have role PARENT, was " + parent.role);
        }
        if (parent == this) {
            throw new IllegalArgumentException("An account cannot be its own parent");
        }
        this.parent = parent;
    }

    /** Links this account to a Google identity (account linking). */
    public void linkGoogle(String googleId) {
        this.googleId = requireNonBlank(googleId, "googleId");
    }

    /** Sets the BCrypt password hash for local login. */
    public void setPassword(String passwordHash) {
        this.password = requireNonBlank(passwordHash, "password");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
