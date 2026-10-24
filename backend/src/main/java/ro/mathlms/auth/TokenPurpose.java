package ro.mathlms.auth;

/** What a signed one-time token is allowed to be used for. */
public enum TokenPurpose {
    VERIFY_EMAIL,
    PASSWORD_RESET,
    INVITE,
    ERASE_ACCOUNT,
    /** The emailed download link of a GDPR export archive. */
    GDPR_EXPORT
}
