package ro.mathlms.tutor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.teaching.TeacherScope;
import ro.mathlms.tutor.TutorHintService.TutorHintsDto;
import ro.mathlms.tutor.TutorHintService.TutorSettingsDto;

/**
 * The tutor in the question editor: whether it is switched on, and its hint proposals for one question (a POST because
 * it calls the model and costs). The browser never reaches the tutor service itself - only through here, after the
 * same ownership check as editing the question.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class TutorController {

    private final TutorHintService service;
    private final TeacherScope scope;

    public TutorController(TutorHintService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @GetMapping("/api/admin/tutor/settings")
    public TutorSettingsDto settings() {
        return service.settings();
    }

    @PostMapping("/api/admin/quiz-items/{itemId}/tutor-hints")
    public TutorHintsDto suggest(@PathVariable Long itemId) {
        scope.requireQuizItem(itemId);
        return service.suggest(itemId);
    }

    @ExceptionHandler(TutorException.class)
    public ResponseEntity<String> unavailable(TutorException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
    }
}
