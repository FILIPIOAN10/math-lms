package ro.mathlms.quiz;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/**
 * „De ce am greșit?": the student asks from their result; the teacher reads which tests have questions waiting. The
 * explanation itself is saved through {@code PUT /api/admin/quiz-options/{id}/mistake}, as any other.
 */
@RestController
public class WhyWrongController {

    private final WhyWrongService service;
    private final ExplanationRequests requests;
    private final TeacherScope scope;

    public WhyWrongController(WhyWrongService service, ExplanationRequests requests, TeacherScope scope) {
        this.service = service;
        this.requests = requests;
        this.scope = scope;
    }

    @PostMapping("/api/quiz/attempts/{attemptId}/items/{itemId}/why-wrong")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> ask(@PathVariable Long attemptId, @PathVariable Long itemId, Authentication auth) {
        service.ask(attemptId, itemId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    /** The teacher's tests with explanations asked for, oldest request first (every test for an ADMIN). */
    @GetMapping("/api/admin/explanation-requests")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<ExplanationRequests.PendingDto> pending() {
        return requests.pending(scope.restricted() ? scope.currentUserId() : null);
    }

    @ExceptionHandler(WhyWrongService.RefusedException.class)
    public ResponseEntity<String> refused(WhyWrongService.RefusedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
