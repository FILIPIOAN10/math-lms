package ro.mathlms.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Encrypts the authenticator secrets at rest (AES-256-GCM, random 96-bit IV, 128-bit tag) with TOTP_ENC_KEY - a key of
 * its own, not JWT_SECRET - so a database dump or backup alone cannot produce anyone's codes.
 *
 * <p>Stored format {@code v1:<base64(iv + ciphertext + tag)>}. The version says how it was sealed, so the format can
 * change later without guessing; a value without it is the pre-phase-2 format, sealed with a key derived from
 * JWT_SECRET, and is still read. {@link TotpKeyMigration} re-seals such values (and those sealed with
 * TOTP_ENC_KEY_PREVIOUS during a rotation) with the current key at start, so nobody has to enrol their phone again.
 */
@Component
public class TotpSecretCipher {

    static final String V1 = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec current;
    private final Optional<SecretKeySpec> previous;
    private final SecretKeySpec legacy;

    public TotpSecretCipher(SecretKeys keys) {
        this.current = keys.totp();
        this.previous = keys.totpPrevious();
        this.legacy = keys.totpLegacy();
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, current, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            return V1 + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the TOTP secret", e);
        }
    }

    public String decrypt(String stored) {
        try {
            if (stored.startsWith(V1)) {
                byte[] all = Base64.getDecoder().decode(stored.substring(V1.length()));
                try {
                    return open(current, all);
                } catch (GeneralSecurityException wrongKey) {
                    if (previous.isEmpty()) {
                        throw wrongKey;
                    }
                    return open(previous.get(), all);
                }
            }
            return open(legacy, Base64.getDecoder().decode(stored));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Could not decrypt the TOTP secret (was TOTP_ENC_KEY changed without TOTP_ENC_KEY_PREVIOUS?)", e);
        }
    }

    /** True when the value is in the current format and sealed with the current key: nothing to re-encrypt. */
    public boolean isCurrent(String stored) {
        if (!stored.startsWith(V1)) {
            return false;
        }
        try {
            open(current, Base64.getDecoder().decode(stored.substring(V1.length())));
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** The same secret sealed again with the current key in the current format. */
    public String reencrypt(String stored) {
        return encrypt(decrypt(stored));
    }

    private static String open(SecretKeySpec key, byte[] all) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
        return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.US_ASCII);
    }
}
