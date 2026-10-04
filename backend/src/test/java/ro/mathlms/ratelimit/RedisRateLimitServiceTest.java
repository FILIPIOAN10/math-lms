package ro.mathlms.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import ro.mathlms.TestcontainersConfiguration;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The atomic window against a REAL Redis, plus the fail-open behaviour when Redis misbehaves. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RedisRateLimitServiceTest {

    @Autowired
    private RedisRateLimitService service;

    private RateLimitRule rule(long limit) {
        return new RateLimitRule("test", "GET", "/x", limit, Duration.ofMinutes(1), RateLimitKeyType.IP);
    }

    @Test
    void allowsUpToTheLimitThenBlocksWithAPositiveRetryAfter() {
        String key = "rate_limit:test:" + UUID.randomUUID();

        for (int i = 1; i <= 3; i++) {
            assertThat(service.checkLimit(key, rule(3)).allowed()).as("request " + i).isTrue();
        }
        RateLimitResult blocked = service.checkLimit(key, rule(3));

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remainingRequests()).isZero();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void everyKeyHasItsOwnBudget() {
        String a = "rate_limit:test:" + UUID.randomUUID();
        String b = "rate_limit:test:" + UUID.randomUUID();

        assertThat(service.checkLimit(a, rule(1)).allowed()).isTrue();
        assertThat(service.checkLimit(a, rule(1)).allowed()).isFalse();
        assertThat(service.checkLimit(b, rule(1)).allowed()).isTrue();
    }

    @Test
    void remainingCountsDown() {
        String key = "rate_limit:test:" + UUID.randomUUID();

        assertThat(service.checkLimit(key, rule(5)).remainingRequests()).isEqualTo(4);
        assertThat(service.checkLimit(key, rule(5)).remainingRequests()).isEqualTo(3);
    }

    @Test
    void aRedisOutageFailsOpenInsteadOfBreakingTheApi() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("redis is down"));

        RateLimitResult result = new RedisRateLimitService(broken).checkLimit("k", rule(5));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remainingRequests()).isEqualTo(5);
    }
}
