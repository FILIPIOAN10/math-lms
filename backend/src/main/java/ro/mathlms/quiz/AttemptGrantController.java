package ro.mathlms.quiz;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.teaching.TeacherScope;

/** "+1 încercare" for the student of one paper, from the grading page or the homework list (ADMIN, TEACHER). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AttemptGrantController {

    /** How many graded attempts the student may now hand in at the test. */
    public record ExtraAttemptDto(int attemptsAllowed) {
    }

    private final AttemptGrantService service;
    private final TeacherScope scope;

    public AttemptGrantController(AttemptGrantService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @PostMapping("/api/admin/quiz/attempts/{attemptId}/extra-attempt")
    public ExtraAttemptDto grantOne(@PathVariable Long attemptId) {
        scope.requireAttempt(attemptId); // a teacher only for papers of their own classes' tests
        return new ExtraAttemptDto(service.grantOne(attemptId));
    }
}
