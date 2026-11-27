package ro.mathlms.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2: versioned TOTP ciphertext under TOTP_ENC_KEY, still reading what was stored before. */
class TotpSecretCipherTest {

    static final String JWT = "jwt-secret-at-least-32-characters-long!!";
    static final String EMAIL = "email-token-secret-32-characters-long!!!";
    static final String OLD = "old-totp-encryption-key-32-characters-long";
    static final String NEW = "new-totp-encryption-key-32-characters-long";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    static TotpSecretCipher cipher(String key, String previous) {
        AuthProperties auth = new AuthProperties(List.of(), List.of(), JWT, 15);
        return new TotpSecretCipher(new SecretKeys(auth, new KeyProperties(EMAIL, key, previous, true)));
    }

    @Test
    void encryptsInTheVersionedFormatAndRoundTrips() {
        TotpSecretCipher cipher = cipher(NEW, null);
        String stored = cipher.encrypt(SECRET);

        assertThat(stored).startsWith("v1:");
        assertThat(cipher.decrypt(stored)).isEqualTo(SECRET);
        assertThat(cipher.isCurrent(stored)).isTrue();
        assertThat(cipher.encrypt(SECRET)).isNotEqualTo(stored); // a fresh IV every time
    }

    @Test
    void readsTheFormatStoredBeforePhase2AndReSealsIt() throws Exception {
        String legacy = sealLikeBeforePhase2(SECRET);
        TotpSecretCipher cipher = cipher(NEW, null);

        assertThat(cipher.decrypt(legacy)).isEqualTo(SECRET);
        assertThat(cipher.isCurrent(legacy)).isFalse();
        String resealed = cipher.reencrypt(legacy);
        assertThat(resealed).startsWith("v1:");
        assertThat(cipher.isCurrent(resealed)).isTrue();
        assertThat(cipher.decrypt(resealed)).isEqualTo(SECRET);
    }

    @Test
    void duringARotationReadsThePreviousKeyAndReSealsWithTheNewOne() {
        String underOld = cipher(OLD, null).encrypt(SECRET);
        TotpSecretCipher rotating = cipher(NEW, OLD);

        assertThat(rotating.decrypt(underOld)).isEqualTo(SECRET);
        assertThat(rotating.isCurrent(underOld)).isFalse();
        assertThat(cipher(NEW, null).decrypt(rotating.reencrypt(underOld))).isEqualTo(SECRET);
    }

    @Test
    void aChangedKeyWithoutThePreviousOneFailsLoudly() {
        String underOld = cipher(OLD, null).encrypt(SECRET);

        assertThatThrownBy(() -> cipher(NEW, null).decrypt(underOld))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TOTP_ENC_KEY_PREVIOUS");
    }

    /** Exactly what TotpSecretCipher wrote before phase 2: no version, key = SHA-256("mathlms-totp-secret:" + JWT). */
    static String sealLikeBeforePhase2(String secret) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(("mathlms-totp-secret:" + JWT).getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] sealed = c.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + sealed.length).put(iv).put(sealed).array());
    }
}
