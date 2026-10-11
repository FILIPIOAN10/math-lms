package ro.mathlms.student;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.mastery.MasteryService;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;
import ro.mathlms.review.ReviewDtos.ReviewStatusDto;
import ro.mathlms.review.ReviewService;
import ro.mathlms.student.StudentProfileDto.ClassRef;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

/**
 * T5: everything the teacher needs about ONE student on one page — classes, parent, homework, grades, progress,
 * chapter map and daily review. It reuses exactly what the student and the parent see (the same services), so the
 * three views can never disagree. Any id that is not a student account answers 404.
 */
@Service
public class AdminStudentService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptService quizAttemptService;
    private final AssignmentService assignmentService;
    private final MasteryService masteryService;
    private final ReviewService reviewService;

    public AdminStudentService(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                               QuizAttemptService quizAttemptService, AssignmentService assignmentService,
                               MasteryService masteryService, ReviewService reviewService) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.quizAttemptService = quizAttemptService;
        this.assignmentService = assignmentService;
        this.masteryService = masteryService;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public StudentProfileDto profile(Long studentId) {
        User student = requireStudent(studentId);
        List<ClassRef> classes = enrollmentRepository.findByStudentIdFetchClass(studentId).stream()
                .map(e -> new ClassRef(e.getSchoolClass().getId(), e.getSchoolClass().getName()))
                .toList();
        User parent = student.getParent();
        return new StudentProfileDto(student.getId(), student.getFullName(), student.getEmail(), student.getStatus(),
                classes,
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getFullName(),
                parent == null ? null : parent.getEmail());
    }

    public List<MyAttemptDto> attempts(Long studentId) {
        return quizAttemptService.listMyAttempts(requireStudent(studentId).getEmail());
    }

    public List<StudentAssignmentDto> assignments(Long studentId) {
        return assignmentService.myAssignments(requireStudent(studentId).getEmail());
    }

    public List<ProgressPointDto> progress(Long studentId) {
        return quizAttemptService.getProgress(requireStudent(studentId).getEmail());
    }

    public List<ChapterMasteryDto> mastery(Long studentId) {
        return masteryService.forStudent(requireStudent(studentId).getEmail());
    }

    public ReviewStatusDto review(Long studentId) {
        return reviewService.status(requireStudent(studentId).getId());
    }

    private User requireStudent(Long studentId) {
        return userRepository.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }
}
