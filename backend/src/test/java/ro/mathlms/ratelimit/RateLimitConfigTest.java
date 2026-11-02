package ro.mathlms.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final List<RateLimitRule> rules = new RateLimitConfig().rateLimitRules(true);

    /** Every button that spends money on a model answers to a daily limit per teacher. */
    @Test
    void aPaidMistakeSuggestionHasADailyLimitPerTeacher() {
        RateLimitRule rule = rules.stream()
                .filter(r -> r.method().equals("POST")
                        && new AntPathMatcher().match(r.pathPattern(), "/api/admin/quiz-options/7/mistake-suggestion"))
                .findFirst().orElseThrow();

        assertThat(rule.window()).isEqualTo(Duration.ofDays(1));
        assertThat(rule.keyType()).isEqualTo(RateLimitKeyType.USER);
    }

    @Test
    void noRulesWhenSwitchedOff() {
        assertThat(new RateLimitConfig().rateLimitRules(false)).isEmpty();
    }
}
