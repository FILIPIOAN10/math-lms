package ro.mathlms.student;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;
import ro.mathlms.review.ReviewDtos.ReviewStatusDto;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/** T5: the teacher's page for one student, under {@code /api/admin/students/{id}/...} (ADMIN). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AdminStudentController {

    private final AdminStudentService service;
    private final TeacherScope scope;

    public AdminStudentController(AdminStudentService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    /** Classes and latest result per student, for the list of students (a TEACHER gets their own students). */
    @GetMapping("/api/admin/students/summary")
    public List<StudentListRowDto> summaries() {
        return scope.keepOwnedStudents(service.listRows(), StudentListRowDto::studentId);
    }

    @GetMapping("/api/admin/students/{studentId}")
    public StudentProfileDto profile(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return service.profile(studentId);
    }

    @GetMapping("/api/admin/students/{studentId}/attempts")
    public List<MyAttemptDto> attempts(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return service.attempts(studentId);
    }

    @GetMapping("/api/admin/students/{studentId}/assignments")
    public List<StudentAssignmentDto> assignments(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return service.assignments(studentId);
    }

    @GetMapping("/api/admin/students/{studentId}/progress")
    public List<ProgressPointDto> progress(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return service.progress(studentId);
    }

    @GetMapping("/api/admin/students/{studentId}/mastery")
    public List<ChapterMasteryDto> mastery(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return service.mastery(studentId);
    }

    @GetMapping("/api/admin/students/{studentId}/review")
    public ReviewStatusDto review(@PathVariable Long studentId) {
        scope.requireStudent(studentId);
        return service.review(studentId);
    }

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<String> handleNotFound(StudentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
