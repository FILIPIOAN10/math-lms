package ro.mathlms.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.review.ReviewDtos.ChapterPracticeResultDto;
import ro.mathlms.review.ReviewDtos.SimilarPracticeDto;

/** „Dă-mi una asemănătoare": the nearest grile and worked examples to a question, and the check of an answer. */
@RestController
@PreAuthorize("hasRole('STUDENT')")
public class SimilarPracticeController {

    public record SimilarAnswerRequest(@NotNull Long optionId) {
    }

    private final SimilarPracticeService service;

    public SimilarPracticeController(SimilarPracticeService service) {
        this.service = service;
    }

    @GetMapping("/api/quiz/items/{itemId}/similar")
    public SimilarPracticeDto similar(@PathVariable Long itemId, Authentication auth) {
        return service.similar(auth.getName(), itemId);
    }

    @PostMapping("/api/quiz/practice/items/{itemId}/answer")
    public ChapterPracticeResultDto answer(@PathVariable Long itemId, @Valid @RequestBody SimilarAnswerRequest request,
                                           Authentication auth) {
        return service.answer(auth.getName(), itemId, request.optionId());
    }
}
