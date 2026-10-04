package ro.mathlms.quiz;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.quiz.QuizDtos.QuizSummaryDto;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;

import java.util.List;
import java.util.Optional;

/**
 * The student quiz-taking API, under {@code /api/quiz/...}. Access needs an active account
 * (STATUS_ACTIVE, enforced in {@code SecurityConfig}) and the STUDENT role. The current student
 * is taken from the authenticated principal (their email) — never from the request body.
 */
@RestController
@PreAuthorize("hasRole('STUDENT')")
public class QuizAttemptController {

    private final QuizAttemptService service;

    public QuizAttemptController(QuizAttemptService service) {
        this.service = service;
    }

    @GetMapping("/api/quiz/quizzes")
    public List<QuizSummaryDto> list(Authentication auth) {
        return service.listPublished(auth.getName()).stream().map(QuizSummaryDto::from).toList();
    }

    /** {@code ?mode=PRACTICE} starts a practice (immediate feedback, no grade); the default is the graded test. */
    @PostMapping("/api/quiz/quizzes/{quizId}/attempts")
    public StartedAttemptDto start(@PathVariable Long quizId,
                                   @RequestParam(defaultValue = "TEST") AttemptMode mode, Authentication auth) {
        return service.startAttempt(quizId, auth.getName(), mode);
    }

    /** The student's own progress chart data: graded attempts over time. */
    @GetMapping("/api/quiz/progress")
    public List<ProgressPointDto> progress(Authentication auth) {
        return service.getProgress(auth.getName());
    }

    @GetMapping("/api/quiz/attempts")
    public List<MyAttemptDto> myAttempts(Authentication auth) {
        return service.listMyAttempts(auth.getName());
    }

    @PutMapping("/api/quiz/attempts/{attemptId}/responses/{itemId}")
    public ResponseEntity<AnswerFeedbackDto> answer(@PathVariable Long attemptId, @PathVariable Long itemId,
                                                    @Valid @RequestBody AnswerRequest request, Authentication auth) {
        return feedbackOrNoContent(service.saveResponse(attemptId, itemId, request.optionId(), auth.getName()));
    }

    @PostMapping("/api/quiz/attempts/{attemptId}/responses/{itemId}/photo")
    public ResponseEntity<AnswerFeedbackDto> uploadPhoto(@PathVariable Long attemptId, @PathVariable Long itemId,
                                                         @RequestParam("file") MultipartFile file, Authentication auth) {
        return feedbackOrNoContent(service.uploadOpenPhoto(attemptId, itemId, file, auth.getName()));
    }

    /** 200 + feedback in a practice, 204 (nothing revealed) in a graded test. */
    private static ResponseEntity<AnswerFeedbackDto> feedbackOrNoContent(Optional<AnswerFeedbackDto> feedback) {
        return feedback.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/quiz/attempts/{attemptId}/submit")
    public AttemptResultDto submit(@PathVariable Long attemptId, Authentication auth) {
        return service.submit(attemptId, auth.getName());
    }

    @GetMapping("/api/quiz/attempts/{attemptId}/result")
    public AttemptResultViewDto result(@PathVariable Long attemptId, Authentication auth) {
        return service.getResult(attemptId, auth.getName());
    }
}
