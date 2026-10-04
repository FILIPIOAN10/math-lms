package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InviteTokenServiceTest {

    private AuthProperties properties;
    private InviteTokenService service;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of(),
                "test-secret-at-least-32-characters-long!!",
                60);
        service = new InviteTokenService(properties);
    }

    @Test
    void roundTripsTheRole() {
        assertThat(service.verify(service.generate(Role.STUDENT))).isEqualTo(Role.STUDENT);
        assertThat(service.verify(service.generate(Role.PARENT))).isEqualTo(Role.PARENT);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.generate(Role.STUDENT);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        // Issue the token 8 days in the past so its 7-day invite TTL is already spent.
        Clock past = Clock.fixed(Instant.now().minus(8, ChronoUnit.DAYS), ZoneOffset.UTC);
        InviteTokenService issuedInThePast = new InviteTokenService(properties, past);
        String token = issuedInThePast.generate(Role.STUDENT);

        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenIssuedForAnotherPurpose() {
        // A token validly signed with the same key but carrying purpose=VERIFY_EMAIL,
        // so the invite service must refuse it. Built inline to avoid pulling in Redis.
        var key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        String verifyEmailToken = Jwts.builder()
                .subject("ana@scoala.ro")
                .claim("purpose", TokenPurpose.VERIFY_EMAIL.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> service.verify(verifyEmailToken))
                .isInstanceOf(JwtException.class);
    }
}
