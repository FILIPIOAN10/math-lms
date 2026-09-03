package ro.mathlms.gdpr;

/**
 * Erasure request body. {@code password} is required for local accounts (re-authentication) and
 * absent for Google-only accounts, which are verified by the emailed confirmation link alone.
 */
public record ErasureRequest(String password) {
}
