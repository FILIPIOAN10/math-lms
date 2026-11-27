package ro.mathlms.auth;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * An account-wide ceiling on WRONG passwords, on top of the per-IP filter (credential stuffing comes from many IPs).
 * Only failures count: a stranger who knows the admin's address cannot lock them out by sending requests, only by
 * guessing wrong {@link #MAX_FAILURES} times - and then only for {@link #WINDOW}, after which the counter expires. A
 * successful sign-in clears it.
 *
 * <p>Redis sees a keyed digest of the address (key from {@link SecretKeys}), never the address. Fail-closed: when
 * Redis cannot be read, {@link #requireNotBlocked} throws {@link LoginRateLimitUnavailableException} BEFORE the
 * password is checked, so "401 vs 500" can never become a password oracle.
 */
@Service
public class LoginAccountRateLimiter {

    static final int MAX_FAILURES = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String PREFIX = "login-failures:";

    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final boolean enabled;
    private final byte[] key;

    public LoginAccountRateLimiter(StringRedisTemplate redis, MeterRegistry meters, SecretKeys keys,
                                   @Value("${rate.limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.meters = meters;
        this.enabled = enabled;
        this.key = keys.loginPseudonyms();
    }

    /** Throws when this account has had too many wrong passwords lately, or when that cannot be checked. */
    public void requireNotBlocked(String email) {
        if (!enabled) {
            return;
        }
        String redisKey = redisKey(email);
        String value;
        Long ttl;
        try {
            value = redis.opsForValue().get(redisKey);
            ttl = value == null ? null : redis.getExpire(redisKey);
        } catch (RuntimeException e) {
            throw new LoginRateLimitUnavailableException(e);
        }
        if (value != null && parse(value) >= MAX_FAILURES) {
            meters.counter("rate_limit_blocked", "rule", "login-account").increment();
            long retryAfter = ttl == null || ttl <= 0 ? WINDOW.toSeconds() : ttl;
            throw new LoginRateLimitExceededException(retryAfter);
        }
    }

    /** Counts one wrong password; the window starts at the first failure. Best effort: Redis was readable a moment ago. */
    public void recordFailure(String email) {
        if (!enabled) {
            return;
        }
        String redisKey = redisKey(email);
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, WINDOW);
            }
        } catch (RuntimeException ignored) {
            // The next attempt's requireNotBlocked fails closed if Redis is really gone.
        }
    }

    /** A right password: the account starts clean again. */
    public void recordSuccess(String email) {
        if (!enabled) {
            return;
        }
        try {
            redis.delete(redisKey(email));
        } catch (RuntimeException ignored) {
            // Harmless: the counter expires by itself.
        }
    }

    String redisKey(String email) {
        return PREFIX + digest(email);
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String digest(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] normalized = email == null ? new byte[0]
                    : email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(normalized));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
