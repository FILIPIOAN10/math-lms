package ro.mathlms.content;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin management of class enrollments. All routes are under {@code /api/admin/...} (ADMIN):
 * list the roster of a class, enroll a student, and remove an enrollment.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/classes/{classId}/enrollments")
    public List<EnrollmentDto> roster(@PathVariable Long classId) {
        return service.roster(classId).stream().map(EnrollmentDto::from).toList();
    }

    @PostMapping("/api/admin/classes/{classId}/enrollments")
    public ResponseEntity<EnrollmentDto> enroll(@PathVariable Long classId,
                                                @Valid @RequestBody EnrollRequest request) {
        Enrollment created = service.enroll(classId, request.studentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentDto.from(created));
    }

    @DeleteMapping("/api/admin/enrollments/{id}")
    public ResponseEntity<Void> unenroll(@PathVariable Long id) {
        service.unenroll(id);
        return ResponseEntity.noContent().build();
    }
}
