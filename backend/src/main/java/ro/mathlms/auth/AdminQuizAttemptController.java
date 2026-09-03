package ro.mathlms.auth;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import ro.mathlms.content.GradeRequestDto;
import ro.mathlms.quiz.QuizAttemptService;

@RestController
@RequestMapping("/api/admin/quiz/attempts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuizAttemptController {

    private final QuizAttemptService service;

    public AdminQuizAttemptController(QuizAttemptService service) {
        this.service = service;
    }

    @GetMapping("/{attemptId}/responses/{itemId}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long attemptId, @PathVariable Long itemId) {
        Resource file = service.getOpenPhotoResource(attemptId, itemId);

        // Determină MediaType-ul automat din numele fișierului
        MediaType mediaType = MediaTypeFactory.getMediaType(file)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .contentType(mediaType)
                .body(file);
    }

    @PutMapping("/{attemptId}/responses/{itemId}/grade")
    public ResponseEntity<Void> gradeItem(@PathVariable Long attemptId,
                                          @PathVariable Long itemId,
                                          @Valid @RequestBody GradeRequestDto request) {
        service.gradeOpenResponse(attemptId, itemId, request.points());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{attemptId}/mark-graded")
    public ResponseEntity<Void> finalizeGrading(@PathVariable Long attemptId) {
        service.finalizeGrading(attemptId);
        return ResponseEntity.noContent().build();
    }
}