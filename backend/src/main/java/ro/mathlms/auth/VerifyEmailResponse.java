package ro.mathlms.auth;

import ro.mathlms.user.AccountStatus;

/** The account's state right after its email is confirmed: ACTIVE (may sign in) or PENDING_APPROVAL. */
public record VerifyEmailResponse(AccountStatus status) {
}
