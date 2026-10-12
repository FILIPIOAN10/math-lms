package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import ro.mathlms.TestcontainersConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VerificationTokenServiceTest {

    @Autowired
    private VerificationTokenService service;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final AuthProperties properties = new AuthProperties(
            List.of("profesor@gmail.com"), List.of(),
            "test-secret-at-least-32-characters-long!!", 60);

    @Test
    void roundTripsEmailForMatchingPurpose() {
        String token = service.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL);

        assertThat(service.verify(token, TokenPurpose.VERIFY_EMAIL)).isEqualTo("ana@scoala.ro");
    }

    @Test
    void tokenIsSingleUse() {
        String token = service.generate("ana@scoala.ro", TokenPurpose.PASSWORD_RESET);

        assertThat(service.verify(token, TokenPurpose.PASSWORD_RESET)).isEqualTo("ana@scoala.ro");
        // A replay of the same token must now fail.
        assertThatThrownBy(() -> service.verify(token, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenUsedForWrongPurpose() {
        String token = service.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL);

        assertThatThrownBy(() -> service.verify(token, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL);
        // Change one character in the MIDDLE of the signature, always to a different one. (It used to overwrite the last
        // two with "xx": a no-op when the token already ended that way, and the final base64url character carries bits
        // the decoder ignores - so now and then the "tampered" token was still valid and the test failed.)
        int at = token.lastIndexOf('.') + 10;
        char swapped = token.charAt(at) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, at) + swapped + token.substring(at + 1);
        assertThat(tampered).isNotEqualTo(token);

        assertThatThrownBy(() -> service.verify(tampered, TokenPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        // Issue the token 48h in the past so its 24h verification TTL is already spent.
        Clock past = Clock.fixed(Instant.now().minus(48, ChronoUnit.HOURS), ZoneOffset.UTC);
        VerificationTokenService issuedInThePast = new VerificationTokenService(properties, past, redisTemplate);
        String token = issuedInThePast.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL);

        assertThatThrownBy(() -> service.verify(token, TokenPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
    }
}
