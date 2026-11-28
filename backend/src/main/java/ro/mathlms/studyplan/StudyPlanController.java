package ro.mathlms.studyplan;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.parent.ParentAccessException;
import ro.mathlms.studyplan.StudyPlanService.ClassGoalRowDto;
import ro.mathlms.studyplan.StudyPlanService.GoalSummaryDto;
import ro.mathlms.studyplan.StudyPlanService.StudyPlanDto;
import ro.mathlms.teaching.TeacherScope;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * „Ținta la EN" and „Planul până la EN" for everyone who may see them: the student's own (they pick the target), their
 * parent's read-only view (their own child only - the parent API's uniform 403), and the teacher's, per student (with
 * the pins) and per class. The exam date is read by any teacher and set by an admin, like the EN chapter pages.
 */
@RestController
public class StudyPlanController {

    public record GoalRequest(BigDecimal targetGrade) {
    }

    public record ExamDateDto(LocalDate examDate) {
    }

    private final StudyPlanService service;
    private final EnGoalService goalService;
    private final UserRepository userRepository;
    private final TeacherScope scope;

    public StudyPlanController(StudyPlanService service, EnGoalService goalService, UserRepository userRepository,
                               TeacherScope scope) {
        this.service = service;
        this.goalService = goalService;
        this.userRepository = userRepository;
        this.scope = scope;
    }

    // --- the student ---

    @GetMapping("/api/quiz/study-plan")
    @PreAuthorize("hasRole('STUDENT')")
    public StudyPlanDto myPlan(Authentication auth) {
        return service.planFor(me(auth));
    }

    @GetMapping("/api/quiz/en-goal")
    @PreAuthorize("hasRole('STUDENT')")
    public GoalSummaryDto myGoal(Authentication auth) {
        return service.summaryFor(me(auth));
    }

    @PutMapping("/api/quiz/en-goal")
    @PreAuthorize("hasRole('STUDENT')")
    public StudyPlanDto setMyGoal(@RequestBody GoalRequest request, Authentication auth) {
        return service.setGoal(me(auth), request == null ? null : request.targetGrade());
    }

    private User me(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // --- the parent: read only ---

    @GetMapping("/api/parent/children/{studentId}/study-plan")
    @PreAuthorize("hasRole('PARENT')")
    public StudyPlanDto childPlan(@PathVariable Long studentId, Authentication auth) {
        return service.planFor(child(studentId, auth));
    }

    @GetMapping("/api/parent/children/{studentId}/en-goal")
    @PreAuthorize("hasRole('PARENT')")
    public GoalSummaryDto childGoal(@PathVariable Long studentId, Authentication auth) {
        return service.summaryFor(child(studentId, auth));
    }

    private User child(Long studentId, Authentication auth) {
        User parent = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ParentAccessException("Contul de părinte nu există."));
        return userRepository.findByIdAndParentId(studentId, parent.getId())
                .orElseThrow(() -> new ParentAccessException("Acesta nu e copilul tău."));
    }

    // --- the teacher ---

    @GetMapping("/api/admin/students/{studentId}/study-plan")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public StudyPlanDto studentPlan(@PathVariable Long studentId) {
        return service.planFor(student(studentId));
    }

    @GetMapping("/api/admin/students/{studentId}/en-goal")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public GoalSummaryDto studentGoal(@PathVariable Long studentId) {
        return service.summaryFor(student(studentId));
    }

    /** Puts a chapter first into the student's plan; the answer is the plan with it. */
    @PutMapping("/api/admin/students/{studentId}/study-plan/pins/{chapterId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public StudyPlanDto pin(@PathVariable Long studentId, @PathVariable Long chapterId) {
        return service.pin(student(studentId), chapterId, scope.currentUserId());
    }

    @DeleteMapping("/api/admin/students/{studentId}/study-plan/pins/{chapterId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public StudyPlanDto unpin(@PathVariable Long studentId, @PathVariable Long chapterId) {
        return service.unpin(student(studentId), chapterId);
    }

    @GetMapping("/api/admin/classes/{classId}/en-goals")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public List<ClassGoalRowDto> forClass(@PathVariable Long classId) {
        scope.requireClass(classId);
        return service.forClass(classId);
    }

    /** A student of one of the teacher's classes (any student for an admin), else 404. */
    private User student(Long studentId) {
        scope.requireStudent(studentId);
        return userRepository.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // --- the exam date ---

    @GetMapping("/api/admin/en-exam-date")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ExamDateDto examDate() {
        return new ExamDateDto(goalService.examDate().orElse(null));
    }

    @PutMapping("/api/admin/en-exam-date")
    @PreAuthorize("hasRole('ADMIN')")
    public ExamDateDto setExamDate(@RequestBody ExamDateDto request) {
        return new ExamDateDto(goalService.setExamDate(request == null ? null : request.examDate()).orElse(null));
    }
}
