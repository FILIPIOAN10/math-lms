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

/**
 * School classes. Reads live under {@code /api/classes} (any active account); writes
 * under {@code /api/admin/classes} (ADMIN — enforced by the URL rule in SecurityConfig
 * and the method {@code @PreAuthorize}).
 */
@RestController
public class SchoolClassController {

    private final SchoolClassService service;
    private final ContentAccess access;
    private final EnrollmentService enrollmentService;

    public SchoolClassController(SchoolClassService service, ContentAccess access,
                                 EnrollmentService enrollmentService) {
        this.service = service;
        this.access = access;
        this.enrollmentService = enrollmentService;
    }

    /** Every class for admins/parents; a student only gets the classes they are enrolled in. */
    @GetMapping("/api/classes")
    public List<SchoolClassDto> list(Authentication authentication) {
        List<SchoolClass> classes = access.isStudent(authentication)
                ? enrollmentService.myClasses(authentication.getName())
                : service.list();
        return classes.stream().map(SchoolClassDto::from).toList();
    }

    @GetMapping("/api/classes/{id}")
    public SchoolClassDto get(@PathVariable Long id, Authentication authentication) {
        access.checkClass(id, authentication);
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
