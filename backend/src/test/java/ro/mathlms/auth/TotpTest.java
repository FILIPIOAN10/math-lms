package ro.mathlms.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B: the SHA1 seed "12345678901234567890" (the last 6 of the 8-digit values). */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors() {
        assertThat(Totp.code(RFC_KEY, 59 / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.code(RFC_KEY, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void base32RoundTripsAndMatchesTheRfc4648Example() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Totp.unbase32("MZXW6YTBOI")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
        String secret = Totp.newSecret();
        assertThat(Totp.unbase32(secret)).hasSize(20);
    }

    @Test
    void acceptsTheCurrentCodeAndOneStepEitherSideButNeverTheSameStepTwice() {
        String secret = Totp.base32(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1234567890L);
        long step = Totp.stepOf(now);

        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step), now, null)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step - 2), now, null)).isEmpty(); // too old
        // replay: the step already used (or an earlier one) is refused
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step), now, step)).isEmpty();
        assertThat(Totp.verify(secret, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "abcdef", now, null)).isEmpty();
    }

    @Test
    void theOtpauthAddressNamesTheIssuerAndAccount() {
        assertThat(Totp.otpauthUri("Math LMS", "ana@scoala.ro", "ABC"))
                .isEqualTo("otpauth://totp/Math%20LMS%3Aana%40scoala.ro?secret=ABC&issuer=Math%20LMS&algorithm=SHA1&digits=6&period=30");
    }
}
