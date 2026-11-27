package ro.mathlms.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secrets that must NOT be JWT_SECRET (security plan, phase 2): one key per job, so each can be rotated on its own and a
 * leak of one does not open the others.
 *
 * <ul>
 *   <li>{@code emailTokenSecret} (EMAIL_TOKEN_SECRET) signs the links we email or hand out: account verification,
 *       password reset, erasure, GDPR export and role invites.</li>
 *   <li>{@code totpKey} (TOTP_ENC_KEY) encrypts the authenticator secrets at rest.</li>
 *   <li>{@code totpPreviousKey} (TOTP_ENC_KEY_PREVIOUS) is set only while rotating TOTP_ENC_KEY: secrets sealed with it
 *       are still read and are re-encrypted with the new key at the next start ({@link TotpKeyMigration}).</li>
 *   <li>{@code acceptLegacyLinkTokens} (ACCEPT_LEGACY_LINK_TOKENS, default true) keeps accepting links signed with
 *       JWT_SECRET before this change. Every such link expires within 7 days: turn it off a week after the deploy.</li>
 * </ul>
 *
 * Empty values are derived from JWT_SECRET (separate keys, but rotated together with it): fine for local dev and tests,
 * never in production, where docker-compose.prod.yml requires them.
 */
@ConfigurationProperties(prefix = "app.keys")
public record KeyProperties(
        String emailTokenSecret,
        String totpKey,
        String totpPreviousKey,
        Boolean acceptLegacyLinkTokens
) {

    /** Nothing explicit: every key derived from JWT_SECRET, legacy links accepted (tests, local dev). */
    public static KeyProperties derived() {
        return new KeyProperties(null, null, null, true);
    }

    boolean acceptsLegacyLinkTokens() {
        return acceptLegacyLinkTokens == null || acceptLegacyLinkTokens;
    }
}
