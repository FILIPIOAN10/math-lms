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
import ro.mathlms.assignment.AssignmentDtos.MultiClassAssignmentRequest;
import ro.mathlms.assignment.AssignmentDtos.OverrideRequest;
import ro.mathlms.assignment.AssignmentDtos.PublishAndAssignRequest;
import ro.mathlms.assignment.AssignmentDtos.ReleaseRequest;
import ro.mathlms.assignment.AssignmentDtos.RescheduleRequest;
import ro.mathlms.assignment.AssignmentDtos.StudentStatusDto;
import ro.mathlms.assignment.AssignmentDtos.StudentsAssignmentRequest;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/** The teacher's homework API, under {@code /api/admin/assignments} (ADMIN). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AssignmentAdminController {

    private final AssignmentService service;
    private final PublishAndAssignService publishAndAssignService;
    private final TeacherScope scope;

    public AssignmentAdminController(AssignmentService service, PublishAndAssignService publishAndAssignService,
                                     TeacherScope scope) {
        this.service = service;
        this.publishAndAssignService = publishAndAssignService;
        this.scope = scope;
    }

    @GetMapping("/api/admin/assignments")
    public List<AssignmentSummaryDto> list() {
        return scope.keepOwnedClasses(service.list(), AssignmentSummaryDto::schoolClassId);
    }

    @PostMapping("/api/admin/assignments")
    public ResponseEntity<AssignmentSummaryDto> create(@Valid @RequestBody AssignmentRequest request) {
        scope.requireQuiz(request.quizId());
        scope.requireClass(request.schoolClassId());
        Assignment created = service.create(request.quizId(), request.schoolClassId(), request.dueAt(),
                request.availableFrom());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.summaryOf(created.getId()));
    }

    /** One quiz to several classes in one go, all or nothing; answers with one summary per class, in the order sent. */
    @PostMapping("/api/admin/assignments/batch")
    public ResponseEntity<List<AssignmentSummaryDto>> createForClasses(
            @Valid @RequestBody MultiClassAssignmentRequest request) {
        scope.requireQuiz(request.quizId());
        request.schoolClassIds().forEach(scope::requireClass);
        List<Assignment> created = service.createForClasses(request.quizId(), request.schoolClassIds(), request.dueAt(),
                request.availableFrom());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(created.stream().map(a -> service.summaryOf(a.getId())).toList());
    }

    /**
     * Catch-up homework: a quiz for some students of one class only. The service checks each is a student of that class
     * (a teacher's own class, checked here).
     */
    @PostMapping("/api/admin/assignments/for-students")
    public ResponseEntity<AssignmentSummaryDto> createForStudents(@Valid @RequestBody StudentsAssignmentRequest request) {
        scope.requireQuiz(request.quizId());
        scope.requireClass(request.schoolClassId());
        Assignment created = service.createForStudents(request.quizId(), request.schoolClassId(), request.studentIds(),
                request.dueAt(), request.availableFrom());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.summaryOf(created.getId()));
    }

    /** T3: publishes a draft quiz and gives it as homework, all or nothing. */
    @PostMapping("/api/admin/quizzes/{quizId}/publish-and-assign")
    public ResponseEntity<AssignmentSummaryDto> publishAndAssign(@PathVariable Long quizId,
                                                                 @Valid @RequestBody PublishAndAssignRequest request) {
        scope.requireQuiz(quizId);
        scope.requireClass(request.schoolClassId());
        Assignment created = publishAndAssignService.publishAndAssign(quizId, request.schoolClassId(), request.dueAt(),
                request.availableFrom());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.summaryOf(created.getId()));
    }

    /** Moves the deadline (the quiz and class of an assignment never change - delete and create instead). */
    @PutMapping("/api/admin/assignments/{id}")
    public AssignmentSummaryDto reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        scope.requireAssignment(id);
        service.reschedule(id, request.dueAt());
        return service.summaryOf(id);
    }

    /** Moves a scheduled homework's release, or gives it now (null); refused once its students can see it. */
    @PutMapping("/api/admin/assignments/{id}/release")
    public AssignmentSummaryDto setRelease(@PathVariable Long id, @RequestBody ReleaseRequest request) {
        scope.requireAssignment(id);
        service.setRelease(id, request.availableFrom());
        return service.summaryOf(id);
    }

    @DeleteMapping("/api/admin/assignments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scope.requireAssignment(id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * One student's exception: their own deadline and/or excused; neither puts them back on the class deadline. Answers
     * with the refreshed hand-in list. The service checks the student is in the homework's class (a teacher's own class).
     */
    @PutMapping("/api/admin/assignments/{id}/students/{studentId}/override")
    public List<StudentStatusDto> setOverride(@PathVariable Long id, @PathVariable Long studentId,
                                              @RequestBody OverrideRequest request) {
        scope.requireAssignment(id);
        service.setOverride(id, studentId, request.dueAt(), request.excused());
        return service.status(id);
    }

    @GetMapping("/api/admin/assignments/{id}/status")
    public List<StudentStatusDto> status(@PathVariable Long id) {
        scope.requireAssignment(id);
        return service.status(id);
    }
}
