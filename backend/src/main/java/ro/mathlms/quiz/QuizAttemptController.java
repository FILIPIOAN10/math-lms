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
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;

import java.util.List;

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
    public List<QuizSummaryDto> list() {
        return service.listPublished().stream().map(QuizSummaryDto::from).toList();
    }

    @PostMapping("/api/quiz/quizzes/{quizId}/attempts")
    public StartedAttemptDto start(@PathVariable Long quizId, Authentication auth) {
        return service.startAttempt(quizId, auth.getName());
    }

    @PutMapping("/api/quiz/attempts/{attemptId}/responses/{itemId}")
    public ResponseEntity<Void> answer(@PathVariable Long attemptId, @PathVariable Long itemId,
                                       @Valid @RequestBody AnswerRequest request, Authentication auth) {
        service.saveResponse(attemptId, itemId, request.optionId(), auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/quiz/attempts/{attemptId}/responses/{itemId}/photo")
    public ResponseEntity<Void> uploadPhoto(@PathVariable Long attemptId, @PathVariable Long itemId,
                                            @RequestParam("file") MultipartFile file, Authentication auth) {
        service.uploadOpenPhoto(attemptId, itemId, file, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/quiz/attempts/{attemptId}/submit")
    public AttemptResultDto submit(@PathVariable Long attemptId, Authentication auth) {
        return service.submit(attemptId, auth.getName());
    }
}
