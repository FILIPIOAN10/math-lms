package ro.mathlms.parent;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.parent.ParentDtos.ChildDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.review.ReviewDtos.ReviewStatusDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;

import java.util.List;

/** The parent's read-only view of their children. PARENT only; ownership is enforced in {@link ParentService}. */
@RestController
@PreAuthorize("hasRole('PARENT')")
public class ParentController {

    private final ParentService service;

    public ParentController(ParentService service) {
        this.service = service;
    }

    @GetMapping("/api/parent/children")
    public List<ChildDto> children(Authentication auth) {
        return service.myChildren(auth.getName()).stream().map(ChildDto::from).toList();
    }

    @GetMapping("/api/parent/children/{studentId}/attempts")
    public List<MyAttemptDto> attempts(@PathVariable Long studentId, Authentication auth) {
        return service.childAttempts(auth.getName(), studentId);
    }

    /** The child's homework with its state (to do / in progress / handed in, late, overdue). */
    @GetMapping("/api/parent/children/{studentId}/assignments")
    public List<StudentAssignmentDto> assignments(@PathVariable Long studentId, Authentication auth) {
        return service.childAssignments(auth.getName(), studentId);
    }

    /** How the child keeps up with the daily review (E4): done today, left today, streak. */
    @GetMapping("/api/parent/children/{studentId}/review")
    public ReviewStatusDto review(@PathVariable Long studentId, Authentication auth) {
        return service.childReview(auth.getName(), studentId);
    }

    /** The child's level per chapter (E6 mastery map). */
    @GetMapping("/api/parent/children/{studentId}/mastery")
    public List<ChapterMasteryDto> mastery(@PathVariable Long studentId, Authentication auth) {
        return service.childMastery(auth.getName(), studentId);
    }

    @GetMapping("/api/parent/children/{studentId}/progress")
    public List<ProgressPointDto> progress(@PathVariable Long studentId, Authentication auth) {
        return service.childProgress(auth.getName(), studentId);
    }

    @GetMapping("/api/parent/children/{studentId}/attempts/{attemptId}/result")
    public AttemptResultViewDto result(@PathVariable Long studentId, @PathVariable Long attemptId,
                                       Authentication auth) {
        return service.childResult(auth.getName(), studentId, attemptId);
    }
}
