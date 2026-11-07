package ro.mathlms.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.enchapter.EnChapterNotFoundException;
import ro.mathlms.review.ReviewDtos.ChapterPracticeDto;
import ro.mathlms.review.ReviewDtos.ChapterPracticeResultDto;

/** The student practises one EN chapter from its page, under {@code /api/quiz/en-chapters/{id}/practice}. */
@RestController
@PreAuthorize("hasRole('STUDENT')")
public class ChapterPracticeController {

    public record ChapterPracticeAnswerRequest(@NotNull Long optionId) {
    }

    private final ChapterPracticeService service;

    public ChapterPracticeController(ChapterPracticeService service) {
        this.service = service;
    }

    @GetMapping("/api/quiz/en-chapters/{chapterId}/practice")
    public ChapterPracticeDto practice(@PathVariable Long chapterId, Authentication auth) {
        return service.practice(auth.getName(), chapterId);
    }

    @PostMapping("/api/quiz/en-chapters/{chapterId}/practice/items/{itemId}/answer")
    public ChapterPracticeResultDto answer(@PathVariable Long chapterId, @PathVariable Long itemId,
                                           @Valid @RequestBody ChapterPracticeAnswerRequest request, Authentication auth) {
        return service.answer(auth.getName(), chapterId, itemId, request.optionId());
    }

    @ExceptionHandler(EnChapterNotFoundException.class)
    public ResponseEntity<String> notFound(EnChapterNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Capitolul nu mai există.");
    }
}
