package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.quiz.QuizDtos.QuizSummaryDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.HintDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.QuizPreviewDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;

import java.util.Optional;
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
        when(service.listPublished("elev@scoala.ro")).thenReturn(List.of(published));

        List<QuizSummaryDto> result = controller.list(auth);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.title()).isEqualTo("Simulare EN");
            assertThat(dto.status()).isEqualTo(QuizStatus.PUBLISHED);
        });
    }

    @Test
    void progressDelegatesWithPrincipalEmail() {
        when(service.getProgress("elev@scoala.ro")).thenReturn(List.of());

        assertThat(controller.progress(auth)).isEmpty();
        verify(service).getProgress("elev@scoala.ro");
    }

    @Test
    void startDelegatesWithPrincipalEmail() {
        StartedAttemptDto dto = new StartedAttemptDto(50L, QuizAttemptStatus.IN_PROGRESS, null, List.of(), null, null, AttemptMode.TEST, List.of());
        when(service.startAttempt(10L, "elev@scoala.ro", AttemptMode.TEST)).thenReturn(dto);

        assertThat(controller.start(10L, AttemptMode.TEST, auth)).isEqualTo(dto);
    }

    @Test
    void startPassesThePracticeModeThrough() {
        StartedAttemptDto dto = new StartedAttemptDto(51L, QuizAttemptStatus.IN_PROGRESS, null, List.of(), null, null, AttemptMode.PRACTICE, List.of());
        when(service.startAttempt(10L, "elev@scoala.ro", AttemptMode.PRACTICE)).thenReturn(dto);

        assertThat(controller.start(10L, AttemptMode.PRACTICE, auth)).isEqualTo(dto);
    }

    @Test
    void myAttemptsDelegatesWithPrincipalEmail() {
        List<MyAttemptDto> mine = List.of(new MyAttemptDto(
                50L, 10L, "Simulare EN", QuizAttemptStatus.GRADED, null, null, 7, AttemptMode.TEST, 35));
        when(service.listMyAttempts("elev@scoala.ro")).thenReturn(mine);

        assertThat(controller.myAttempts(auth)).isEqualTo(mine);
    }

    @Test
    void answerReturns204AndDelegatesOption() {
        ResponseEntity<AnswerFeedbackDto> response =
                controller.answer(50L, 100L, new AnswerRequest(1000L), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT); // a graded test reveals nothing
        assertThat(response.getBody()).isNull();
        verify(service).saveResponse(50L, 100L, 1000L, "elev@scoala.ro");
    }

    @Test
    void aPracticeAnswerReturns200WithTheFeedback() {
        AnswerFeedbackDto feedback = new AnswerFeedbackDto(true, 1000L, "barem");
        when(service.saveResponse(50L, 100L, 1000L, "elev@scoala.ro")).thenReturn(Optional.of(feedback));

        ResponseEntity<AnswerFeedbackDto> response =
                controller.answer(50L, 100L, new AnswerRequest(1000L), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(feedback);
    }

    @Test
    void uploadPhotoReturns204AndDelegates() {
        MultipartFile file = new MockMultipartFile("file", "r.jpg", "image/jpeg", "bytes".getBytes());

        ResponseEntity<AnswerFeedbackDto> response = controller.uploadPhoto(50L, 101L, file, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).uploadOpenPhoto(50L, 101L, file, "elev@scoala.ro");
    }

    @Test
    void previewDelegatesWithPrincipalEmail() {
        QuizPreviewDto preview = new QuizPreviewDto(10L, "Simulare EN", null, 30, false, 3, 11);
        when(service.getPreview(10L, "elev@scoala.ro")).thenReturn(preview);

        assertThat(controller.preview(10L, auth)).isEqualTo(preview);
    }

    @Test
    void ownPhotoIsServedInlineWithItsImageType() {
        Resource image = new ByteArrayResource("img".getBytes()) {
            @Override
            public String getFilename() {
                return "stored.jpg";
            }
        };
        when(service.getOwnPhoto(50L, 101L, "elev@scoala.ro")).thenReturn(image);

        ResponseEntity<Resource> response = controller.ownPhoto(50L, 101L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline; filename=\"stored.jpg\"");
        // a re-upload replaces the photo under the same URL: the browser must ask again, never reuse an old copy
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isSameAs(image);
    }

    @Test
    void resultDelegatesToService() {
        AttemptResultViewDto view = new AttemptResultViewDto(
                50L, "Simulare EN", QuizAttemptStatus.GRADED, 5, 35, List.of(), AttemptMode.TEST, null);
        when(service.getResult(50L, "elev@scoala.ro")).thenReturn(view);

        assertThat(controller.result(50L, auth)).isEqualTo(view);
    }

    @Test
    void submitDelegatesAndReturnsResult() {
        AttemptResultDto dto =
                new AttemptResultDto(50L, QuizAttemptStatus.GRADED, 5, 5, 5);
        when(service.submit(50L, "elev@scoala.ro")).thenReturn(dto);

        assertThat(controller.submit(50L, auth)).isEqualTo(dto);
    }

    @Test
    void hintDelegatesWithPrincipalEmail() {
        HintDto hint = new HintDto(1, "indiciul 1", 3);
        when(service.revealHint(50L, 100L, 1, "elev@scoala.ro")).thenReturn(hint);

        assertThat(controller.hint(50L, 100L, 1, auth)).isEqualTo(hint);
    }
}
