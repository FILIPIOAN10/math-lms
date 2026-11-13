package ro.mathlms.tutor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.tutor.StudentTutorService.ExplanationDto;
import ro.mathlms.tutor.StudentTutorService.StatusDto;

import java.util.List;

/**
 * The student's "Nu înțeleg…" box: whether it is open for them, and an explanation from the lessons (a POST because it
 * calls the model and counts against the day's questions). The browser never reaches the tutor service itself.
 */
@RestController
@PreAuthorize("hasRole('STUDENT')")
public class StudentTutorController {

    /** {@code history}: the box's last messages, oldest first, only so a short follow-up can be understood. */
    public record ExplainRequest(@NotBlank @Size(max = 500) String question,
                                 @Size(max = 6) List<@Valid @NotNull TurnRequest> history) {
    }

    public record TurnRequest(@NotNull @Pattern(regexp = "user|bot") String role,
                              @NotBlank @Size(max = 1000) String text) {
    }

    private final StudentTutorService service;

    public StudentTutorController(StudentTutorService service) {
        this.service = service;
    }

    @GetMapping("/api/quiz/tutor")
    public StatusDto status(Authentication auth) {
        return service.status(auth.getName());
    }

    @PostMapping("/api/quiz/tutor/explain")
    public ExplanationDto explain(@Valid @RequestBody ExplainRequest request, Authentication auth) {
        List<TutorClient.Turn> history = request.history() == null ? List.of()
                : request.history().stream().map(t -> new TutorClient.Turn(t.role(), t.text())).toList();
        return service.explain(auth.getName(), request.question(), history);
    }

    @ExceptionHandler(StudentTutorService.ClosedException.class)
    public ResponseEntity<String> closed(StudentTutorService.ClosedException ex) {
        return ResponseEntity.status(ex.status()).body(ex.getMessage());
    }

    @ExceptionHandler(TutorException.class)
    public ResponseEntity<String> unavailable(TutorException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
    }
}
