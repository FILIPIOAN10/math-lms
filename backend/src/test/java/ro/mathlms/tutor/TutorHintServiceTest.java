package ro.mathlms.tutor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizOptionRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Switched off, the tutor is not there at all: 404, and neither the database nor the service is touched. */
class TutorHintServiceTest {

    @Test
    void switchedOffItIsNotFoundAndAsksNobody() {
        TutorClient client = mock(TutorClient.class);
        QuizItemRepository items = mock(QuizItemRepository.class);
        TutorHintService service = new TutorHintService(
                new TutorProperties(false, "http://localhost:8001", "t", Duration.ofSeconds(55)), client, items,
                mock(QuizOptionRepository.class), mock(QuizItemPartRepository.class), mock(PlatformTransactionManager.class));

        assertThat(service.settings().enabled()).isFalse();
        assertThatThrownBy(() -> service.suggest(7L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(client, items);
    }
}
