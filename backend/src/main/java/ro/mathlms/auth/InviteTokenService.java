package ro.mathlms.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ro.mathlms.user.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies role-scoped invite tokens. An invite link is created by an
 * admin before the invitee is known, so — unlike {@link VerificationTokenService} —
 * the token carries a {@link Role} rather than an email. Stateless: an HMAC-signed
 * JWS with the role, the purpose and an expiry; nothing is stored server-side.
 */
@Service
public class InviteTokenService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public InviteTokenService(AuthProperties authProperties) {
        this(authProperties, Clock.systemUTC());
    }

    // Test seam: a fixed clock lets us produce already-expired tokens.
    InviteTokenService(AuthProperties authProperties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generate(Role role) {
        Instant now = clock.instant();
        Instant expiry = now.plus(INVITE_TTL);

        return Jwts.builder()
                .claim(PURPOSE_CLAIM, TokenPurpose.INVITE.name())
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the role carried by the invite token.
     *
     * @throws JwtException if the token is tampered, expired, issued for another
     *                      purpose, or carries an unknown role
     */
    public Role verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get(PURPOSE_CLAIM, String.class);
        if (!TokenPurpose.INVITE.name().equals(purpose)) {
            throw new JwtException("Token purpose mismatch: expected "
                    + TokenPurpose.INVITE + " but was " + purpose);
        }

        String role = claims.get(ROLE_CLAIM, String.class);
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Invite token carries an unknown role: " + role);
        }
    }
}
