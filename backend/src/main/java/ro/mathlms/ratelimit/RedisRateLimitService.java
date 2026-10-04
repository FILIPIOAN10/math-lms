package ro.mathlms.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * The atomic core: a fixed-window counter in Redis. INCR + EXPIRE run as ONE Lua script — done in two
 * round trips, two requests could both see count==1 and each set a TTL, and the window would never really
 * close. The script returns {@code count * 100000 + ttl} so one number carries both.
 *
 * <p>Fail OPEN: any Redis trouble allows the request (and logs a warning). A rate limiter must never take the
 * whole API down; brute-force protection is lost only while Redis is unreachable.
 */
@Service
public class RedisRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitService.class);

    /** Larger than any sane TTL in seconds, so count and ttl never collide in the packed value. */
    private static final long PACK = 100_000L;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "local ttl = redis.call('TTL', KEYS[1]) "
                    + "return current * 100000 + math.max(ttl, 0)",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult checkLimit(String key, RateLimitRule rule) {
        long windowSeconds = rule.window().toSeconds();
        Long packed;
        try {
            packed = redisTemplate.execute(SCRIPT, Collections.singletonList(key), String.valueOf(windowSeconds));
        } catch (RuntimeException e) {
            log.warn("Rate limiting unavailable for {} ({}); failing open", key, e.toString());
            return new RateLimitResult(true, 0, rule.limit(), windowSeconds);
        }
        if (packed == null) {
            log.warn("Rate limiting got no answer from Redis for {}; failing open", key);
            return new RateLimitResult(true, 0, rule.limit(), windowSeconds);
        }
        long current = packed / PACK;
        long retryAfter = packed % PACK;
        if (retryAfter <= 0) {
            retryAfter = windowSeconds;
        }
        return new RateLimitResult(current <= rule.limit(), current, Math.max(rule.limit() - current, 0), retryAfter);
    }
}
