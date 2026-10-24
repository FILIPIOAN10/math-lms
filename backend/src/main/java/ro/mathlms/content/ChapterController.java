package ro.mathlms.content;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import ro.mathlms.teaching.TeacherScope;

/**
 * Chapters within a book. Reads nested under the book ({@code /api/books/{bookId}/chapters});
 * a single chapter at {@code /api/chapters/{id}}. Writes under {@code /api/admin/...} (ADMIN).
 */
@RestController
public class ChapterController {

    private final ChapterService service;
    private final ContentAccess access;
    private final TeacherScope scope;

    public ChapterController(ChapterService service, ContentAccess access, TeacherScope scope) {
        this.service = service;
        this.access = access;
        this.scope = scope;
    }

    @GetMapping("/api/books/{bookId}/chapters")
    public List<ChapterDto> listByBook(@PathVariable Long bookId, Authentication authentication) {
        access.checkBook(bookId, authentication);
        return service.listByBook(bookId).stream().map(ChapterDto::from).toList();
    }

    @GetMapping("/api/chapters/{id}")
    public ChapterDto get(@PathVariable Long id, Authentication authentication) {
        access.checkChapter(id, authentication);
        return ChapterDto.from(service.get(id));
    }

    @PostMapping("/api/admin/books/{bookId}/chapters")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ChapterDto> create(@PathVariable Long bookId,
                                             @Valid @RequestBody ChapterRequest request) {
        scope.requireBook(bookId);
        Chapter created = service.create(bookId, request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChapterDto.from(created));
    }

    @PutMapping("/api/admin/chapters/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ChapterDto update(@PathVariable Long id, @Valid @RequestBody ChapterRequest request) {
        scope.requireChapter(id);
        return ChapterDto.from(service.update(id, request.title(), request.description()));
    }

    @DeleteMapping("/api/admin/chapters/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scope.requireChapter(id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
