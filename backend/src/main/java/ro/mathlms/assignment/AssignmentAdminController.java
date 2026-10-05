package ro.mathlms.assignment;

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
import ro.mathlms.assignment.AssignmentDtos.AssignmentRequest;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.assignment.AssignmentDtos.RescheduleRequest;
import ro.mathlms.assignment.AssignmentDtos.StudentStatusDto;

import java.util.List;

/** The teacher's homework API, under {@code /api/admin/assignments} (ADMIN). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AssignmentAdminController {

    private final AssignmentService service;

    public AssignmentAdminController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/assignments")
    public List<AssignmentSummaryDto> list() {
        return service.list();
    }

    @PostMapping("/api/admin/assignments")
    public ResponseEntity<AssignmentSummaryDto> create(@Valid @RequestBody AssignmentRequest request) {
        Assignment created = service.create(request.quizId(), request.schoolClassId(), request.dueAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.summaryOf(created.getId()));
    }

    /** Moves the deadline (the quiz and class of an assignment never change - delete and create instead). */
    @PutMapping("/api/admin/assignments/{id}")
    public AssignmentSummaryDto reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        service.reschedule(id, request.dueAt());
        return service.summaryOf(id);
    }

    @DeleteMapping("/api/admin/assignments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/assignments/{id}/status")
    public List<StudentStatusDto> status(@PathVariable Long id) {
        return service.status(id);
    }
}
