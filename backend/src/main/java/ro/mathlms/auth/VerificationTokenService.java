package ro.mathlms.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies short-lived, single-purpose tokens for the email flows (account verification,
 * password reset, account erasure). Each token carries the email, the purpose, an expiry and a
 * unique {@code jti}. The jti is recorded in Redis at issue and <strong>atomically consumed on the
 * first successful verify</strong> (GETDEL), so a token is single-use: a replay within its TTL
 * fails just like an expired or tampered one. This also makes an outstanding token revocable.
 */
@Service
public class VerificationTokenService {

    private static final Duration VERIFY_EMAIL_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final Duration ERASE_ACCOUNT_TTL = Duration.ofMinutes(30);
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String JTI_PREFIX = "onetime:";

    private final SecretKey key;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public VerificationTokenService(AuthProperties authProperties, StringRedisTemplate redisTemplate) {
        this(authProperties, Clock.systemUTC(), redisTemplate);
    }

    // Test seam: a fixed clock lets us produce already-expired tokens.
    VerificationTokenService(AuthProperties authProperties, Clock clock, StringRedisTemplate redisTemplate) {
        this.key = Keys.hmacShaKeyFor(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
        this.redisTemplate = redisTemplate;
    }

    public String generate(String email, TokenPurpose purpose) {
        Instant now = clock.instant();
        Duration ttl = ttlFor(purpose);
        String jti = UUID.randomUUID().toString();

        // Record the jti as spendable; verify() deletes it on first use (single-use).
        redisTemplate.opsForValue().set(JTI_PREFIX + purpose + ":" + jti, email, ttl);

        return Jwts.builder()
                .subject(email)
                .id(jti)
                .claim(PURPOSE_CLAIM, purpose.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the email carried by the token if it is valid for {@code expectedPurpose} and has not
     * been used before. Consumes the token so it cannot be replayed.
     *
     * @throws JwtException if the token is tampered, expired, issued for another purpose, or already used
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

        // Atomically spend the one-time id; a missing key means already used (or revoked/expired).
        String jti = claims.getId();
        String spent = jti == null ? null
                : redisTemplate.opsForValue().getAndDelete(JTI_PREFIX + expectedPurpose + ":" + jti);
        if (spent == null) {
            throw new JwtException("Token already used or expired");
        }
        return claims.getSubject();
    }

    private Duration ttlFor(TokenPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> VERIFY_EMAIL_TTL;
            case PASSWORD_RESET -> PASSWORD_RESET_TTL;
            case ERASE_ACCOUNT -> ERASE_ACCOUNT_TTL;
            default -> throw new IllegalArgumentException("Unknown token purpose: " + purpose);
        };
    }
}
