package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationTokenServiceTest {

    private AuthProperties properties;
    private VerificationTokenService service;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of(),
                "test-secret-at-least-32-characters-long!!",
                60);
        service = new VerificationTokenService(properties);
    }

    @Test
    void roundTripsEmailForMatchingPurpose() {
        String token = service.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL);

        assertThat(service.verify(token, TokenPurpose.VERIFY_EMAIL)).isEqualTo("ana@scoala.ro");
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
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered, TokenPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        // Issue the token 48h in the past so its 24h verification TTL is already spent.
        Clock past = Clock.fixed(Instant.now().minus(48, ChronoUnit.HOURS), ZoneOffset.UTC);
        VerificationTokenService issuedInThePast = new VerificationTokenService(properties, past);
        String token = issuedInThePast.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL);

        assertThatThrownBy(() -> service.verify(token, TokenPurpose.VERIFY_EMAIL))
                .isInstanceOf(JwtException.class);
    }
}
