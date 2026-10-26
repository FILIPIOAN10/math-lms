package ro.mathlms.aigrading;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.storage.FileService;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What bounds the bill: the daily limit, and nothing queued while the feature is off. */
class AiGradingServiceTest {

    private final AiGradeProposalRepository repository = mock(AiGradeProposalRepository.class);
    private final AnswerGrader grader = mock(AnswerGrader.class);

    private AiGradingService service(boolean enabled, int dailyLimit) {
        return new AiGradingService(repository, mock(QuizAttemptRepository.class), mock(QuizItemRepository.class),
                mock(QuizItemPartRepository.class), mock(ItemResponseRepository.class), mock(FileService.class),
                List.of(grader), new AiGradingProperties(enabled, "fake", dailyLimit, 5, 20), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC), mock(PlatformTransactionManager.class),
                "photos", "images");
    }

    @Test
    void stopsForTheDayOnceTheLimitIsReached() throws Exception {
        // midnight in Bucharest (UTC+2 in late October) is 22:00 UTC the day before
        when(repository.countByStartedAtGreaterThanEqual(Instant.parse("2026-10-25T22:00:00Z"))).thenReturn(300L);

        assertThat(service(true, 300).processNext()).isFalse();

        verify(repository, never()).claimNext();
        verify(grader, never()).grade(any());
    }

    @Test
    void queuesNothingWhileTheFeatureIsOff() {
        service(false, 300).submitted(mock(QuizAttempt.class), List.of(), Map.of());

        verifyNoInteractions(repository);
    }
}
