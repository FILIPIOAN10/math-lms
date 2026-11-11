package ro.mathlms.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;
import ro.mathlms.review.ReviewDtos.ReviewResultDto;
import ro.mathlms.review.ReviewDtos.ReviewTodayDto;
import ro.mathlms.review.ReviewDtos.StudentReviewStatusDto;
import ro.mathlms.teaching.TeacherScope;

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
    private final TeacherScope scope;

    public ReviewController(ReviewService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
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

    /** "Încă 5" once today's review is done; {@code seen} = the ids already shown in this session. */
    @GetMapping("/api/quiz/review/extra")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ReviewQuestionDto> extra(@RequestParam(required = false) List<Long> seen, Authentication auth) {
        return service.extra(auth.getName(), seen);
    }

    /** Checks an "Încă 5" answer; nothing is stored, no box moves. */
    @PostMapping("/api/quiz/review/extra/items/{itemId}/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public ReviewResultDto answerExtra(@PathVariable Long itemId, @Valid @RequestBody ReviewAnswerRequest request,
                                       Authentication auth) {
        return service.answerExtra(auth.getName(), itemId, request.optionId());
    }

    @GetMapping("/api/admin/classes/{classId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<StudentReviewStatusDto> forClass(@PathVariable Long classId) {
        scope.requireClass(classId);
        return service.forClass(classId);
    }
}
