package ro.mathlms.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/**
 * Every signing and encryption key of the app in one place, one key per job (security plan, phase 2):
 * <ul>
 *   <li>access tokens (the session cookie): JWT_SECRET, unchanged;</li>
 *   <li>emailed / shared link tokens (verification, reset, erasure, GDPR export, invites): EMAIL_TOKEN_SECRET;</li>
 *   <li>authenticator secrets at rest: TOTP_ENC_KEY (and TOTP_ENC_KEY_PREVIOUS while rotating it).</li>
 * </ul>
 * Before, all three were JWT_SECRET, so rotating it broke every staff member's two-step sign-in. An explicit key must be
 * at least 32 characters and differ from the others: a bad configuration stops the app at start instead of weakening it.
 */
@Component
public class SecretKeys {

    private static final Logger log = LoggerFactory.getLogger(SecretKeys.class);
    static final int MIN_LENGTH = 32;

    private final SecretKey accessTokens;
    private final SecretKey linkTokens;
    /** The pre-phase-2 link key (JWT_SECRET), still accepted for links already sent; null once turned off. */
    private final SecretKey legacyLinkTokens;
    private final SecretKeySpec totp;
    private final SecretKeySpec totpPrevious;
    /** The pre-phase-2 TOTP key, derived from JWT_SECRET: reads the secrets stored before ciphertexts had a version. */
    private final SecretKeySpec totpLegacy;

    @Autowired
    public SecretKeys(AuthProperties auth, KeyProperties keys) {
        String jwtSecret = Objects.requireNonNull(auth.jwtSecret(), "JWT_SECRET is not set");
        String email = blankToNull(keys.emailTokenSecret());
        String totpKey = blankToNull(keys.totpKey());
        String totpPreviousKey = blankToNull(keys.totpPreviousKey());
        validate(jwtSecret, email, totpKey, totpPreviousKey);

        this.accessTokens = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        if (email == null) {
            log.warn("EMAIL_TOKEN_SECRET is not set: link tokens use a key derived from JWT_SECRET (dev only)");
            this.linkTokens = Keys.hmacShaKeyFor(derive(jwtSecret, "mathlms-link-tokens-v1"));
        } else {
            this.linkTokens = Keys.hmacShaKeyFor(email.getBytes(StandardCharsets.UTF_8));
        }
        this.legacyLinkTokens = keys.acceptsLegacyLinkTokens() ? accessTokens : null;

        if (totpKey == null) {
            log.warn("TOTP_ENC_KEY is not set: authenticator secrets use a key derived from JWT_SECRET (dev only)");
            this.totp = aes(derive(jwtSecret, "mathlms-totp-key-v1"));
        } else {
            this.totp = aes(sha256("mathlms-totp-key:" + totpKey));
        }
        this.totpPrevious = totpPreviousKey == null ? null : aes(sha256("mathlms-totp-key:" + totpPreviousKey));
        this.totpLegacy = aes(sha256("mathlms-totp-secret:" + jwtSecret));
    }

    /** Every key derived from JWT_SECRET, legacy links accepted: for tests that build services by hand. */
    public static SecretKeys derivedFrom(AuthProperties auth) {
        return new SecretKeys(auth, KeyProperties.derived());
    }

    SecretKey accessTokens() {
        return accessTokens;
    }

    SecretKey linkTokens() {
        return linkTokens;
    }

    SecretKeySpec totp() {
        return totp;
    }

    Optional<SecretKeySpec> totpPrevious() {
        return Optional.ofNullable(totpPrevious);
    }

    SecretKeySpec totpLegacy() {
        return totpLegacy;
    }

    /**
     * The claims of an emailed/shared link token: signed with the link key, or - while legacy links are accepted - with
     * JWT_SECRET (links sent before phase 2). The caller still checks purpose, expiry and single use.
     */
    Claims parseLinkToken(String token) {
        try {
            return Jwts.parser().verifyWith(linkTokens).build().parseSignedClaims(token).getPayload();
        } catch (SignatureException e) {
            if (legacyLinkTokens == null) {
                throw e;
            }
            Claims claims = Jwts.parser().verifyWith(legacyLinkTokens).build().parseSignedClaims(token).getPayload();
            log.info("Accepted a link token signed with the legacy key (purpose {})", claims.get("purpose"));
            return claims;
        }
    }

    private static void validate(String jwtSecret, String email, String totpKey, String totpPreviousKey) {
        requireStrong("EMAIL_TOKEN_SECRET", email);
        requireStrong("TOTP_ENC_KEY", totpKey);
        requireStrong("TOTP_ENC_KEY_PREVIOUS", totpPreviousKey);
        requireDifferent("EMAIL_TOKEN_SECRET", email, "JWT_SECRET", jwtSecret);
        requireDifferent("TOTP_ENC_KEY", totpKey, "JWT_SECRET", jwtSecret);
        requireDifferent("TOTP_ENC_KEY", totpKey, "EMAIL_TOKEN_SECRET", email);
        requireDifferent("TOTP_ENC_KEY_PREVIOUS", totpPreviousKey, "TOTP_ENC_KEY", totpKey);
        if (totpPreviousKey != null && totpKey == null) {
            throw new IllegalStateException("TOTP_ENC_KEY_PREVIOUS is set without TOTP_ENC_KEY: set the new key too");
        }
    }

    private static void requireStrong(String name, String value) {
        if (value != null && value.length() < MIN_LENGTH) {
            throw new IllegalStateException(name + " must be at least " + MIN_LENGTH + " characters");
        }
    }

    private static void requireDifferent(String name, String value, String otherName, String other) {
        if (value != null && value.equals(other)) {
            throw new IllegalStateException(name + " must differ from " + otherName);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** HMAC-SHA256(JWT_SECRET, label): a separate key for each label, used only when no explicit key is configured. */
    private static byte[] derive(String secret, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static SecretKeySpec aes(byte[] key) {
        return new SecretKeySpec(key, "AES");
    }
}
