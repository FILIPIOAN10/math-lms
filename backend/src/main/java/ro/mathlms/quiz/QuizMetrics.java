package ro.mathlms.quiz;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Exposes how many timed attempts are past their deadline (plus the grace period) and still IN_PROGRESS
 * ({@code quiz_attempts_overdue}). {@link AttemptExpiryJob} hands them in every 30 s, so a value that stays above zero
 * means the job is late or stuck, and an alert watches it. The gauge reads the table on each scrape; a database hiccup
 * yields NaN, never an error.
 */
@Component
public class QuizMetrics {

    private static final Logger log = LoggerFactory.getLogger(QuizMetrics.class);

    public QuizMetrics(MeterRegistry registry, QuizAttemptRepository repository, Clock clock) {
        Gauge.builder("quiz_attempts_overdue", repository,
                        repo -> overdue(repo, clock.instant().minus(QuizAttemptService.SUBMIT_GRACE)))
                .description("Timed attempts past their deadline that were not handed in yet")
                .register(registry);
    }

    private static double overdue(QuizAttemptRepository repository, Instant cutoff) {
        try {
            return repository.countOverdue(cutoff);
        } catch (RuntimeException e) {
            log.warn("Overdue-attempts gauge could not read the count: {}", e.toString());
            return Double.NaN;
        }
    }
}
