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
 * Chapters within a book. Reads nested under the book ({@code /api/books/{bookId}/chapters});
 * a single chapter at {@code /api/chapters/{id}}. Writes under {@code /api/admin/...} (ADMIN).
 */
@RestController
public class ChapterController {

    private final ChapterService service;

    public ChapterController(ChapterService service) {
        this.service = service;
    }

    @GetMapping("/api/books/{bookId}/chapters")
    public List<ChapterDto> listByBook(@PathVariable Long bookId) {
        return service.listByBook(bookId).stream().map(ChapterDto::from).toList();
    }

    @GetMapping("/api/chapters/{id}")
    public ChapterDto get(@PathVariable Long id) {
        return ChapterDto.from(service.get(id));
    }

    @PostMapping("/api/admin/books/{bookId}/chapters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChapterDto> create(@PathVariable Long bookId,
                                             @Valid @RequestBody ChapterRequest request) {
        Chapter created = service.create(bookId, request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChapterDto.from(created));
    }

    @PutMapping("/api/admin/chapters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ChapterDto update(@PathVariable Long id, @Valid @RequestBody ChapterRequest request) {
        return ChapterDto.from(service.update(id, request.title(), request.description()));
    }

    @DeleteMapping("/api/admin/chapters/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
