package ro.mathlms.quiz;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptDetailDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptSummaryDto;

import java.util.List;

/**
 * Teacher-side grading of quiz attempts, under {@code /api/admin/quiz/attempts/...} (ADMIN — the
 * teacher role in this system). Serves the uploaded rezolvare photo, awards manual points on OPEN
 * items, and finalises the attempt once every open item is scored.
 */
@RestController
@RequestMapping("/api/admin/quiz/attempts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuizAttemptController {

    private final QuizAttemptService service;

    public AdminQuizAttemptController(QuizAttemptService service) {
        this.service = service;
    }

    /** The grading queue. Defaults to SUBMITTED (waiting for the teacher); ?status=GRADED shows history. */
    @GetMapping
    public List<AdminAttemptSummaryDto> list(
            @RequestParam(defaultValue = "SUBMITTED") QuizAttemptStatus status) {
        return service.listForGrading(status);
    }

    @GetMapping("/{attemptId}")
    public AdminAttemptDetailDto detail(@PathVariable Long attemptId) {
        return service.getAttemptForGrading(attemptId);
    }

    @GetMapping("/{attemptId}/responses/{itemId}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long attemptId, @PathVariable Long itemId) {
        Resource file = service.getOpenPhotoResource(attemptId, itemId);
        MediaType mediaType = MediaTypeFactory.getMediaType(file)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                .contentType(mediaType)
                .body(file);
    }

    @PutMapping("/{attemptId}/responses/{itemId}/grade")
    public ResponseEntity<Void> gradeItem(@PathVariable Long attemptId, @PathVariable Long itemId,
                                          @Valid @RequestBody GradeRequestDto request) {
        service.gradeOpenResponse(attemptId, itemId, request.points(), request.comment());
        return ResponseEntity.noContent().build();
    }

    /** The teacher's comment on the whole paper (while grading, or after); a blank one removes it. */
    @PutMapping("/{attemptId}/comment")
    public ResponseEntity<Void> comment(@PathVariable Long attemptId,
                                        @Valid @RequestBody AttemptCommentRequest request) {
        service.commentOnAttempt(attemptId, request.comment());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{attemptId}/mark-graded")
    public ResponseEntity<Void> finalizeGrading(@PathVariable Long attemptId) {
        service.finalizeGrading(attemptId);
        return ResponseEntity.noContent().build();
    }
}
