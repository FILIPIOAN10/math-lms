package ro.mathlms.mastery;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.mastery.EnMasteryService.EnChapterMasteryDto;
import ro.mathlms.mastery.EnMasteryService.EnClassMasteryDto;
import ro.mathlms.parent.ParentAccessException;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

/**
 * The map of EN chapters (stage 4) for everyone who may see it: the student's own, their parent's view (their own
 * child only - the same 403 as the rest of the parent API), and the teacher's, per student and per class.
 */
@RestController
public class EnMasteryController {

    private final EnMasteryService service;
    private final UserRepository userRepository;

    public EnMasteryController(EnMasteryService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/quiz/en-mastery")
    @PreAuthorize("hasRole('STUDENT')")
    public List<EnChapterMasteryDto> mine(Authentication auth) {
        User student = userRepository.findByEmail(auth.getName())
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return service.forStudent(student);
    }

    @GetMapping("/api/parent/children/{studentId}/en-mastery")
    @PreAuthorize("hasRole('PARENT')")
    public List<EnChapterMasteryDto> child(@PathVariable Long studentId, Authentication auth) {
        User parent = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ParentAccessException("Unknown parent account"));
        User child = userRepository.findByIdAndParentId(studentId, parent.getId())
                .orElseThrow(() -> new ParentAccessException("Not your child"));
        return service.forStudent(child);
    }

    @GetMapping("/api/admin/students/{studentId}/en-mastery")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EnChapterMasteryDto> student(@PathVariable Long studentId) {
        User student = userRepository.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return service.forStudent(student);
    }

    @GetMapping("/api/admin/classes/{classId}/en-mastery")
    @PreAuthorize("hasRole('ADMIN')")
    public EnClassMasteryDto forClass(@PathVariable Long classId) {
        return service.forClass(classId);
    }
}
