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
 * School classes. Reads live under {@code /api/classes} (any active account); writes
 * under {@code /api/admin/classes} (ADMIN — enforced by the URL rule in SecurityConfig
 * and the method {@code @PreAuthorize}).
 */
@RestController
public class SchoolClassController {

    private final SchoolClassService service;
    private final ContentAccess access;
    private final EnrollmentService enrollmentService;
    private final TeacherScope scope;

    public SchoolClassController(SchoolClassService service, ContentAccess access,
                                 EnrollmentService enrollmentService, TeacherScope scope) {
        this.service = service;
        this.access = access;
        this.enrollmentService = enrollmentService;
        this.scope = scope;
    }

    /**
     * Every class for admins; a student gets the classes they are enrolled in, a parent the classes
     * their children attend.
     */
    @GetMapping("/api/classes")
    public List<SchoolClassDto> list(Authentication authentication) {
        List<SchoolClass> classes;
        if (access.isStudent(authentication)) {
            classes = enrollmentService.myClasses(authentication.getName());
        } else if (access.isParent(authentication)) {
            classes = enrollmentService.classesOfMyChildren(authentication.getName());
        } else {
            classes = scope.keepOwnedClasses(service.list(), SchoolClass::getId); // a TEACHER: their own only
        }
        return classes.stream().map(SchoolClassDto::from).toList();
    }

    /** Staff: students, tests and mean grade per class, for the class cards (a TEACHER gets their own classes). */
    @GetMapping("/api/admin/classes/summary")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<ClassSummaryDto> summaries() {
        return scope.keepOwnedClasses(service.summaries(), ClassSummaryDto::classId);
    }

    @GetMapping("/api/classes/{id}")
    public SchoolClassDto get(@PathVariable Long id, Authentication authentication) {
        access.checkClass(id, authentication);
        return SchoolClassDto.from(service.get(id));
    }

    @PostMapping("/api/admin/classes")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<SchoolClassDto> create(@Valid @RequestBody SchoolClassRequest request) {
        SchoolClass created = service.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(SchoolClassDto.from(created));
    }

    @PutMapping("/api/admin/classes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public SchoolClassDto update(@PathVariable Long id, @Valid @RequestBody SchoolClassRequest request) {
        scope.requireClass(id);
        return SchoolClassDto.from(service.update(id, request.name(), request.description()));
    }

    /** ADMIN only: gives the class to another teacher (who then sees it, its students and its homework). */
    @PutMapping("/api/admin/classes/{id}/teacher")
    @PreAuthorize("hasRole('ADMIN')")
    public SchoolClassDto assignTeacher(@PathVariable Long id, @Valid @RequestBody AssignTeacherRequest request) {
        return SchoolClassDto.from(service.assignTeacher(id, request.teacherId()));
    }

    public record AssignTeacherRequest(@jakarta.validation.constraints.NotNull Long teacherId) {
    }

    @DeleteMapping("/api/admin/classes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scope.requireClass(id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
