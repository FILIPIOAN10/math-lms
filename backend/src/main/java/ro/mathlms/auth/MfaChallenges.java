package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import ro.mathlms.user.User;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * The half-done sign-in between the password (or Google) and the authenticator code: an opaque random id in an
 * HttpOnly cookie, mapped to the account in Redis for 5 minutes, with at most 5 code attempts. No session exists
 * until the code is right, and a stolen password alone never yields one.
 */
@Component
public class MfaChallenges {

    public static final String COOKIE = "MATHLMS_MFA";
    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS = 5;
    private static final String PREFIX = "mfa:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final boolean cookieSecure;

    public MfaChallenges(StringRedisTemplate redis, @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
        this.redis = redis;
        this.cookieSecure = cookieSecure;
    }

    /** Starts the second step for {@code user}; the cookie to send. */
    public ResponseCookie start(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + id, user.getEmail(), TTL);
        return cookie(id, TTL);
    }

    /** The email of the pending sign-in in this request's cookie, unless it expired or ran out of attempts. */
    public Optional<String> pending(HttpServletRequest request) {
        String id = id(request);
        if (id == null) {
            return Optional.empty();
        }
        Long attempts = redis.opsForValue().increment(PREFIX + id + ":attempts");
        redis.expire(PREFIX + id + ":attempts", TTL);
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            finish(request);
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + id));
    }

    /** Ends the pending sign-in (done, or given up); the cookie that clears it. */
    public ResponseCookie finish(HttpServletRequest request) {
        String id = id(request);
        if (id != null) {
            redis.delete(PREFIX + id);
            redis.delete(PREFIX + id + ":attempts");
        }
        return cookie("", Duration.ZERO);
    }

    private static String id(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, COOKIE);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        // Only the endpoint that finishes the sign-in ever needs it.
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure)
                .path("/api/auth/2fa").maxAge(maxAge).sameSite("Lax").build();
    }
}
