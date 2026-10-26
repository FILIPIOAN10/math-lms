package ro.mathlms.ingest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.ingest.BaremDtos.AiGradingSettingsDto;
import ro.mathlms.ingest.BaremDtos.BaremReadDto;
import ro.mathlms.quiz.BaremApply;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/**
 * "Barem din poze" in the quiz builder: send the photos of the official barem (read in the background), poll the
 * reading, then save what the teacher kept. Reading proposes only; nothing changes in the test before the PUT.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class BaremController {

    private final BaremService service;
    private final QuizAdminService quizAdminService;
    private final TeacherScope scope;

    public BaremController(BaremService service, QuizAdminService quizAdminService, TeacherScope scope) {
        this.service = service;
        this.quizAdminService = quizAdminService;
        this.scope = scope;
    }

    @GetMapping("/api/admin/ai-grading/settings")
    public AiGradingSettingsDto settings(Authentication auth) {
        return service.settings(auth.getName());
    }

    @PostMapping("/api/admin/quizzes/{quizId}/barem-reads")
    public ResponseEntity<BaremReadDto> read(@PathVariable Long quizId, @RequestParam("files") List<MultipartFile> files,
                                             Authentication auth) {
        scope.requireQuiz(quizId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(auth.getName(), quizId, files));
    }

    @GetMapping("/api/admin/quizzes/{quizId}/barem-reads/{id}")
    public BaremReadDto get(@PathVariable Long quizId, @PathVariable Long id, Authentication auth) {
        scope.requireQuiz(quizId);
        return service.get(auth.getName(), quizId, id);
    }

    @PutMapping("/api/admin/quizzes/{quizId}/barem")
    public ResponseEntity<Void> apply(@PathVariable Long quizId, @Valid @RequestBody BaremApply.Request request) {
        scope.requireQuiz(quizId);
        quizAdminService.applyBarem(quizId, request.items());
        return ResponseEntity.noContent().build();
    }
}
