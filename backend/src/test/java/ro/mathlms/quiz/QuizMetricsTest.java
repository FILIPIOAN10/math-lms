package ro.mathlms.quiz;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizMetricsTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final QuizAttemptRepository repository = mock(QuizAttemptRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void countsTheAttemptsPastDeadlineAndGraceOnEveryRead() {
        Instant cutoff = NOW.minus(QuizAttemptService.SUBMIT_GRACE); // the same cutoff the expiry job uses
        when(repository.countOverdue(cutoff)).thenReturn(0L);
        new QuizMetrics(registry, repository, clock);

        assertThat(registry.get("quiz_attempts_overdue").gauge().value()).isZero();

        when(repository.countOverdue(cutoff)).thenReturn(4L);
        assertThat(registry.get("quiz_attempts_overdue").gauge().value()).isEqualTo(4.0);
    }

    @Test
    void aDatabaseErrorBecomesNaNInsteadOfBreakingTheScrape() {
        when(repository.countOverdue(NOW.minus(QuizAttemptService.SUBMIT_GRACE)))
                .thenThrow(new IllegalStateException("db down"));
        new QuizMetrics(registry, repository, clock);

        assertThat(registry.get("quiz_attempts_overdue").gauge().value()).isNaN();
    }
}
