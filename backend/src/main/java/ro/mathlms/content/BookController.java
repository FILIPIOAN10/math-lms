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
 * Books within a class. Reads are nested under the class ({@code /api/classes/{classId}/books});
 * a single book is at {@code /api/books/{id}}. Writes are under {@code /api/admin/...} (ADMIN).
 */
@RestController
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @GetMapping("/api/classes/{classId}/books")
    public List<BookDto> listByClass(@PathVariable Long classId) {
        return service.listByClass(classId).stream().map(BookDto::from).toList();
    }

    @GetMapping("/api/books/{id}")
    public BookDto get(@PathVariable Long id) {
        return BookDto.from(service.get(id));
    }

    @PostMapping("/api/admin/classes/{classId}/books")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookDto> create(@PathVariable Long classId,
                                          @Valid @RequestBody BookRequest request) {
        Book created = service.create(classId, request.title(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookDto.from(created));
    }

    @PutMapping("/api/admin/books/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BookDto update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return BookDto.from(service.update(id, request.title(), request.description()));
    }

    @DeleteMapping("/api/admin/books/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
