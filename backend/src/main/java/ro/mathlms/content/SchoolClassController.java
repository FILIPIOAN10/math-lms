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
 * School classes. Reads live under {@code /api/classes} (any active account); writes
 * under {@code /api/admin/classes} (ADMIN — enforced by the URL rule in SecurityConfig
 * and the method {@code @PreAuthorize}).
 */
@RestController
public class SchoolClassController {

    private final SchoolClassService service;

    public SchoolClassController(SchoolClassService service) {
        this.service = service;
    }

    @GetMapping("/api/classes")
    public List<SchoolClassDto> list() {
        return service.list().stream().map(SchoolClassDto::from).toList();
    }

    @GetMapping("/api/classes/{id}")
    public SchoolClassDto get(@PathVariable Long id) {
        return SchoolClassDto.from(service.get(id));
    }

    @PostMapping("/api/admin/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SchoolClassDto> create(@Valid @RequestBody SchoolClassRequest request) {
        SchoolClass created = service.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(SchoolClassDto.from(created));
    }

    @PutMapping("/api/admin/classes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SchoolClassDto update(@PathVariable Long id, @Valid @RequestBody SchoolClassRequest request) {
        return SchoolClassDto.from(service.update(id, request.name(), request.description()));
    }

    @DeleteMapping("/api/admin/classes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
