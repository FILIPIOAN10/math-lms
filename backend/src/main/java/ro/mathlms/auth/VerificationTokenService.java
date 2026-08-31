package ro.mathlms.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies short-lived, single-purpose tokens for the email flows
 * (account verification, password reset). Stateless: the token is an HMAC-signed
 * JWS carrying the email, the purpose and an expiry — nothing is stored server-side.
 */
@Service
public class VerificationTokenService {

    private static final Duration VERIFY_EMAIL_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final String PURPOSE_CLAIM = "purpose";

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public VerificationTokenService(AuthProperties authProperties) {
        this(authProperties, Clock.systemUTC());
    }

    // Test seam: a fixed clock lets us produce already-expired tokens.
    VerificationTokenService(AuthProperties authProperties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generate(String email, TokenPurpose purpose) {
        Instant now = clock.instant();
        Instant expiry = now.plus(ttlFor(purpose));

        return Jwts.builder()
                .subject(email)
                .claim(PURPOSE_CLAIM, purpose.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the email carried by the token if it is valid for {@code expectedPurpose}.
     *
     * @throws JwtException if the token is tampered, expired, or issued for another purpose
     */
    public String verify(String token, TokenPurpose expectedPurpose) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get(PURPOSE_CLAIM, String.class);
        if (!expectedPurpose.name().equals(purpose)) {
            throw new JwtException("Token purpose mismatch: expected "
                    + expectedPurpose + " but was " + purpose);
        }
        return claims.getSubject();
    }

    private Duration ttlFor(TokenPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> VERIFY_EMAIL_TTL;
            case PASSWORD_RESET -> PASSWORD_RESET_TTL;
            default -> throw new IllegalArgumentException("Unknown token purpose: " + purpose);
        };
    }
}