package ro.mathlms.tutor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.aiusage.AiUsageRecorder;
import ro.mathlms.enchapter.LessonLinks;
import ro.mathlms.quiz.QuizOptionRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Switched off, "Propune din lecții" is not there: 404, and neither the database nor the tutor is touched. */
class MistakeDraftServiceTest {

    @Test
    void switchedOffItIsNotFoundAndAsksNobody() {
        TutorClient client = mock(TutorClient.class);
        QuizOptionRepository options = mock(QuizOptionRepository.class);
        MistakeDraftService service = new MistakeDraftService(
                new TutorProperties(false, "http://localhost:8001", "t", Duration.ofSeconds(55), false, 20), client,
                mock(TutorHintService.class), options, mock(LessonLinks.class), AiUsageRecorder.NONE,
                mock(PlatformTransactionManager.class));

        assertThatThrownBy(() -> service.draft(7L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(client, options);
    }
}
