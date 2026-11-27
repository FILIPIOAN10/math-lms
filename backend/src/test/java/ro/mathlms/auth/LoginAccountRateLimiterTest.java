package ro.mathlms.auth;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Phase 3: an account ceiling on WRONG passwords only, keyed by a digest, fail-closed when Redis is gone. */
class LoginAccountRateLimiterTest {

    private static final String EMAIL = "Admin@Scoala.ro";

    private final Map<String, String> store = new HashMap<>();
    private StringRedisTemplate redis;
    private LoginAccountRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        when(values.increment(anyString())).thenAnswer(i -> {
            long next = Long.parseLong(store.getOrDefault(i.<String>getArgument(0), "0")) + 1;
            store.put(i.getArgument(0), String.valueOf(next));
            return next;
        });
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
        when(redis.getExpire(anyString())).thenReturn(600L);
        limiter = new LoginAccountRateLimiter(redis, new SimpleMeterRegistry(),
                SecretKeys.derivedFrom(new AuthProperties(List.of(), List.of(),
                        "jwt-secret-at-least-32-characters-long!!", 15)), true);
    }

    @Test
    void tenWrongPasswordsBlockTheAccountForTheWindow() {
        for (int i = 0; i < LoginAccountRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure(EMAIL);
        }
        assertThatCode(() -> limiter.requireNotBlocked(EMAIL)).doesNotThrowAnyException();

        limiter.recordFailure(EMAIL);

        assertThatThrownBy(() -> limiter.requireNotBlocked(EMAIL))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .satisfies(e -> assertThat(((LoginRateLimitExceededException) e).retryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void merelyAskingNeverBlocksSoNobodyCanLockAnAccountByKnockingOnly() {
        for (int i = 0; i < 50; i++) {
            limiter.requireNotBlocked(EMAIL);
        }
        assertThat(store).isEmpty();
    }

    @Test
    void aRightPasswordStartsTheAccountClean() {
        for (int i = 0; i < LoginAccountRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure(EMAIL);
        }
        limiter.recordSuccess(EMAIL);
        limiter.recordFailure(EMAIL);

        assertThatCode(() -> limiter.requireNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void theCaseOfTheAddressDoesNotMatterAndRedisNeverSeesIt() {
        assertThat(limiter.redisKey(EMAIL)).isEqualTo(limiter.redisKey(" admin@scoala.ro "));
        assertThat(limiter.redisKey(EMAIL)).doesNotContain("scoala").doesNotContain("admin");
        assertThat(LoginAccountRateLimiter.WINDOW).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void withoutRedisTheSignInIsRefusedBeforeThePasswordIsChecked() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> limiter.requireNotBlocked(EMAIL)).isInstanceOf(LoginRateLimitUnavailableException.class);
        assertThatCode(() -> limiter.recordFailure(EMAIL)).doesNotThrowAnyException();
    }
}
