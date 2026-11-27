package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2: one key per job, never JWT_SECRET, and a bad configuration stops the app instead of weakening it. */
class SecretKeysTest {

    private static final String JWT = "jwt-secret-at-least-32-characters-long!!";
    private static final String EMAIL = "email-token-secret-32-characters-long!!!";
    private static final String TOTP = "totp-encryption-key-32-characters-long!!";

    private final AuthProperties auth = new AuthProperties(List.of(), List.of(), JWT, 15);

    @Test
    void everyJobHasItsOwnKey() {
        SecretKeys keys = new SecretKeys(auth, new KeyProperties(EMAIL, TOTP, null, true));

        assertThat(keys.linkTokens().getEncoded()).isNotEqualTo(keys.accessTokens().getEncoded());
        assertThat(keys.totp().getEncoded()).isNotEqualTo(keys.totpLegacy().getEncoded());
        assertThat(keys.totpPrevious()).isEmpty();
    }

    @Test
    void withoutExplicitKeysTheyAreDerivedButStillDistinct() {
        SecretKeys keys = SecretKeys.derivedFrom(auth);

        assertThat(keys.linkTokens().getEncoded()).isNotEqualTo(keys.accessTokens().getEncoded());
        assertThat(keys.totp().getEncoded()).isNotEqualTo(keys.totpLegacy().getEncoded());
    }

    @Test
    void rejectsShortKeys() {
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties("too-short", TOTP, null, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMAIL_TOKEN_SECRET");
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties(EMAIL, "too-short", null, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TOTP_ENC_KEY");
    }

    @Test
    void rejectsReusedKeys() {
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties(JWT, TOTP, null, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties(EMAIL, JWT, null, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties(EMAIL, EMAIL, null, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMAIL_TOKEN_SECRET");
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties(EMAIL, TOTP, TOTP, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TOTP_ENC_KEY_PREVIOUS");
    }

    @Test
    void rejectsAPreviousTotpKeyWithoutANewOne() {
        assertThatThrownBy(() -> new SecretKeys(auth, new KeyProperties(EMAIL, null, TOTP, true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TOTP_ENC_KEY_PREVIOUS");
    }

    @Test
    void readsLegacyLinksOnlyWhileAllowed() {
        // A link mailed before phase 2: signed with JWT_SECRET.
        String legacyLink = link(SecretKeys.derivedFrom(auth).accessTokens());

        SecretKeys allowing = new SecretKeys(auth, new KeyProperties(EMAIL, TOTP, null, true));
        assertThat(allowing.parseLinkToken(legacyLink).getSubject()).isEqualTo("ana@scoala.ro");

        SecretKeys strict = new SecretKeys(auth, new KeyProperties(EMAIL, TOTP, null, false));
        assertThatThrownBy(() -> strict.parseLinkToken(legacyLink)).isInstanceOf(SignatureException.class);
    }

    @Test
    void aNewLinkIsNotAnAccessToken() {
        SecretKeys keys = new SecretKeys(auth, new KeyProperties(EMAIL, TOTP, null, true));

        assertThatThrownBy(() -> new JwtService(auth).extractEmail(link(keys.linkTokens())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void anOldLinkSignedWithJwtSecretIsNotAnAccessTokenEither() {
        String legacyLink = link(SecretKeys.derivedFrom(auth).accessTokens());

        assertThatThrownBy(() -> new JwtService(auth).extractEmail(legacyLink))
                .isInstanceOf(JwtException.class).hasMessageContaining("Not an access token");
    }

    private static String link(javax.crypto.SecretKey key) {
        return Jwts.builder().subject("ana@scoala.ro").claim("purpose", "PASSWORD_RESET")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key).compact();
    }
}
