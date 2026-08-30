package ro.mathlms.user;

/**
 * Onboarding lifecycle of an account.
 *
 * <p>Local (email/password) accounts walk the full path:
 * {@code PENDING_VERIFICATION -> PENDING_APPROVAL -> ACTIVE}. Google accounts
 * arrive already email-verified, so they start at {@code PENDING_APPROVAL}.
 * An admin may {@code REJECT} a pending account instead of approving it.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED
}
