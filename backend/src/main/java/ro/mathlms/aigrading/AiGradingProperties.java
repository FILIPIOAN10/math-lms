package ro.mathlms.aigrading;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The barem read from photos and the AI's grade proposals ({@code app.ai.grading.*}), one switch for both - off unless
 * switched on. Read by the provider named in {@code provider} ({@code gemini}, or {@code fake} for dev and tests) with
 * its strongest model. The bill is bounded per day: {@code dailyAnswerLimit} answers proposed (for the whole app) and
 * {@code baremDailyPageLimit} barem photos per teacher, at most {@code baremMaxPages} at a time.
 */
@ConfigurationProperties(prefix = "app.ai.grading")
public record AiGradingProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("gemini") String provider,
        @DefaultValue("300") int dailyAnswerLimit,
        @DefaultValue("5") int baremMaxPages,
        @DefaultValue("20") int baremDailyPageLimit
) {
}
