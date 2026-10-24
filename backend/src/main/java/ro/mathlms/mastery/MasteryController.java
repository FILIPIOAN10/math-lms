package ro.mathlms.mastery;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.mastery.MasteryDtos.ClassMasteryDto;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/** The mastery map (E6): a student's own, and the teacher's grid per class. The parent's is under /api/parent. */
@RestController
public class MasteryController {

    private final MasteryService service;
    private final TeacherScope scope;

    public MasteryController(MasteryService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @GetMapping("/api/quiz/mastery")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ChapterMasteryDto> mine(Authentication auth) {
        return service.forStudent(auth.getName());
    }

    @GetMapping("/api/admin/classes/{classId}/mastery")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ClassMasteryDto forClass(@PathVariable Long classId) {
        scope.requireClass(classId);
        return service.forClass(classId);
    }
}
