package ro.mathlms.assignment;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;

import java.util.List;

/** A student's own homework list. Authorisation is by the signed-in account: only that student's classes are read. */
@RestController
public class AssignmentStudentController {

    private final AssignmentService service;

    public AssignmentStudentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/api/quiz/assignments")
    public List<StudentAssignmentDto> mine(Authentication auth) {
        return service.myAssignments(auth.getName());
    }
}
