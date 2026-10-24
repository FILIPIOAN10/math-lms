package ro.mathlms.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * RFC 6238 time-based one-time passwords, as every authenticator app computes them: HMAC-SHA1 over the 30-second step,
 * 6 digits. Written out (40 lines) rather than pulled in, and checked against the RFC's own test vectors.
 */
public final class Totp {

    static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    /** One step either side: a phone clock a few seconds off (or a code typed as it changes) still works. */
    private static final int WINDOW = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160 random bits, base32 — what the app is given (by QR code or typed in). */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    /** The {@code otpauth://} address an authenticator app reads from the QR code. */
    public static String otpauthUri(String issuer, String account, String secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20") + "&algorithm=SHA1&digits=6&period=30";
    }

    public static long stepOf(Instant now) {
        return now.getEpochSecond() / STEP_SECONDS;
    }

    /**
     * The step {@code code} belongs to, if it is right for {@code now} (±1 step) and later than {@code lastStep}
     * (a code already used — or an older one — is refused, so a code seen over someone's shoulder is worthless).
     */
    public static OptionalLong verify(String secret, String code, Instant now, Long lastStep) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        byte[] key = unbase32(secret);
        long current = stepOf(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (lastStep != null && step <= lastStep) {
                continue;
            }
            if (MessageDigest.isEqual(code(key, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** The 6-digit code of {@code step} (RFC 4226 dynamic truncation). */
    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] unbase32(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "").toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not base32: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
