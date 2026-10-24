package ro.mathlms.grading;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.grading.CommentSnippetDtos.CreateSnippetRequest;
import ro.mathlms.grading.CommentSnippetDtos.SnippetDto;

import java.util.List;

/** The teacher's saved grading comments ("comentarii frecvente"). Each teacher sees only their own. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class CommentSnippetController {

    private final CommentSnippetService service;

    public CommentSnippetController(CommentSnippetService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/comment-snippets")
    public List<SnippetDto> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @PostMapping("/api/admin/comment-snippets")
    public ResponseEntity<SnippetDto> create(@Valid @RequestBody CreateSnippetRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.getName(), request.text()));
    }

    @DeleteMapping("/api/admin/comment-snippets/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/comment-snippets/{id}/use")
    public ResponseEntity<Void> use(@PathVariable Long id, Authentication auth) {
        service.recordUse(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SnippetLimitException.class)
    public ResponseEntity<String> limit(SnippetLimitException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
