package ro.mathlms.content;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exercises within a chapter. Reads nested under the chapter
 * ({@code /api/chapters/{chapterId}/exercises}); a single exercise at
 * {@code /api/exercises/{id}}. Writes under {@code /api/admin/...} (ADMIN). Update carries
 * the exercise version for the optimistic-lock check.
 */
@RestController
public class ExerciseController {

    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping("/api/chapters/{chapterId}/exercises")
    public List<ExerciseDto> listByChapter(@PathVariable Long chapterId) {
        return service.listByChapter(chapterId).stream().map(ExerciseDto::from).toList();
    }

    @GetMapping("/api/exercises/{id}")
    public ExerciseDto get(@PathVariable Long id) {
        return ExerciseDto.from(service.get(id));
    }

    @PostMapping("/api/admin/chapters/{chapterId}/exercises")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExerciseDto> create(@PathVariable Long chapterId,
                                              @Valid @RequestBody ExerciseCreateRequest request) {
        Exercise created = service.create(chapterId, request.statement(),
                request.solution(), request.difficulty());
        return ResponseEntity.status(HttpStatus.CREATED).body(ExerciseDto.from(created));
    }

    @PutMapping("/api/admin/exercises/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ExerciseDto update(@PathVariable Long id, @Valid @RequestBody ExerciseUpdateRequest request) {
        Exercise updated = service.update(id, request.statement(), request.solution(),
                request.difficulty(), request.version());
        return ExerciseDto.from(updated);
    }

    @DeleteMapping("/api/admin/exercises/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
