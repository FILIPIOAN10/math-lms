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
import ro.mathlms.review.ReviewDtos.ReviewResultDto;
import ro.mathlms.review.ReviewDtos.ReviewTodayDto;
import ro.mathlms.review.ReviewDtos.StudentReviewStatusDto;

import java.util.List;

/**
 * The daily review (E4): the student's own queue under {@code /api/quiz/review} (an approved account, like the rest of
 * {@code /api/quiz}), the teacher's class view under {@code /api/admin}. The parent's view is under {@code /api/parent}.
 */
@RestController
public class ReviewController {

    public record ReviewAnswerRequest(@NotNull Long optionId) {
    }

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/api/quiz/review/today")
    @PreAuthorize("hasRole('STUDENT')")
    public ReviewTodayDto today(Authentication auth) {
        return service.today(auth.getName());
    }

    @PostMapping("/api/quiz/review/items/{itemId}/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public ReviewResultDto answer(@PathVariable Long itemId, @Valid @RequestBody ReviewAnswerRequest request,
                                  Authentication auth) {
        return service.answer(auth.getName(), itemId, request.optionId());
    }

    @GetMapping("/api/admin/classes/{classId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StudentReviewStatusDto> forClass(@PathVariable Long classId) {
        return service.forClass(classId);
    }
}
