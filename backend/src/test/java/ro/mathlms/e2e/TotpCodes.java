package ro.mathlms.e2e;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Generator TOTP (RFC 6238) pentru teste, ca să nu încălcăm încapsularea din ro.mathlms.auth.Totp. */
class TotpCodes {

    static String generate(String base32Secret) {
        return generateForStep(base32Secret, System.currentTimeMillis() / 1000 / 30);
    }

    /** Generează codul pentru pasul următor (+30 sec), ca serverul să îl accepte (regula step > lastStep). */
    static String generateNext(String base32Secret) {
        return generateForStep(base32Secret, (System.currentTimeMillis() / 1000 / 30) + 1);
    }

    private static String generateForStep(String base32Secret, long step) {
        try {
            byte[] key = unbase32(base32Secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);

            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new RuntimeException("Nu s-a putut genera codul TOTP", e);
        }
    }

    private static byte[] unbase32(String text) {
        String base32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : text.toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "").toCharArray()) {
            buffer = (buffer << 5) | base32.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}