package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import ro.mathlms.quiz.QuizDtos.QuizSummaryDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAttemptControllerTest {

    private final QuizAttemptService service = mock(QuizAttemptService.class);
    private final QuizAttemptController controller = new QuizAttemptController(service);
    private final Authentication auth =
            new UsernamePasswordAuthenticationToken("elev@scoala.ro", null);

    @Test
    void listMapsPublishedToSummaries() {
        Quiz published = new Quiz("Simulare EN", "d");
        published.publish();
        when(service.listPublished()).thenReturn(List.of(published));

        List<QuizSummaryDto> result = controller.list();

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.title()).isEqualTo("Simulare EN");
            assertThat(dto.status()).isEqualTo(QuizStatus.PUBLISHED);
        });
    }

    @Test
    void startDelegatesWithPrincipalEmail() {
        StartedAttemptDto dto = new StartedAttemptDto(50L, QuizAttemptStatus.IN_PROGRESS, null);
        when(service.startAttempt(10L, "elev@scoala.ro")).thenReturn(dto);

        assertThat(controller.start(10L, auth)).isEqualTo(dto);
    }

    @Test
    void answerReturns204AndDelegatesOption() {
        ResponseEntity<Void> response =
                controller.answer(50L, 100L, new AnswerRequest(1000L), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).saveResponse(50L, 100L, 1000L, "elev@scoala.ro");
    }

    @Test
    void submitDelegatesAndReturnsResult() {
        AttemptResultDto dto =
                new AttemptResultDto(50L, QuizAttemptStatus.GRADED, 5, 5, 5);
        when(service.submit(50L, "elev@scoala.ro")).thenReturn(dto);

        assertThat(controller.submit(50L, auth)).isEqualTo(dto);
    }
}
