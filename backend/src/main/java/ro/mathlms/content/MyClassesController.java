package ro.mathlms.content;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * What the logged-in student attends. Lives under {@code /api/me} (any active account at the URL
 * level; STUDENT only here — admins and parents have no classes of their own).
 */
@RestController
@PreAuthorize("hasRole('STUDENT')")
public class MyClassesController {

    private final EnrollmentService enrollmentService;

    public MyClassesController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/api/me/classes")
    public List<SchoolClassDto> myClasses(Authentication authentication) {
        return enrollmentService.myClasses(authentication.getName()).stream()
                .map(SchoolClassDto::from)
                .toList();
    }
}
