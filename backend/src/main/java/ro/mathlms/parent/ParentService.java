package ro.mathlms.parent;

import org.springframework.stereotype.Service;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentService;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.mastery.MasteryService;
import ro.mathlms.review.ReviewDtos.ReviewStatusDto;
import ro.mathlms.review.ReviewService;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

/**
 * What a parent may see about their children. EVERY read goes through {@link #requireOwnChild}:
 * that single check is the whole security model of this feature — a child is looked up with
 * {@code findByIdAndParentId}, so the database itself refuses anyone else's student.
 */
@Service
public class ParentService {

    private final UserRepository userRepository;
    private final QuizAttemptService quizAttemptService;
    private final AssignmentService assignmentService;
    private final MasteryService masteryService;
    private final ReviewService reviewService;

    public ParentService(UserRepository userRepository, QuizAttemptService quizAttemptService, AssignmentService assignmentService,
                         MasteryService masteryService, ReviewService reviewService) {
        this.assignmentService = assignmentService;
        this.masteryService = masteryService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.quizAttemptService = quizAttemptService;
    }

    public List<User> myChildren(String parentEmail) {
        return userRepository.findByParentIdOrderByFullName(requireParent(parentEmail).getId());
    }

    public List<MyAttemptDto> childAttempts(String parentEmail, Long studentId) {
        User child = requireOwnChild(parentEmail, studentId);
        return quizAttemptService.listMyAttempts(child.getEmail());
    }

    /** The child's homework, exactly as the child sees it (the same derived progress, not done first). */
    public List<StudentAssignmentDto> childAssignments(String parentEmail, Long studentId) {
        User child = requireOwnChild(parentEmail, studentId);
        return assignmentService.myAssignments(child.getEmail());
    }

    /** How the child keeps up with the daily review (E4). */
    public ReviewStatusDto childReview(String parentEmail, Long studentId) {
        User child = requireOwnChild(parentEmail, studentId);
        return reviewService.status(child.getId());
    }

    /** The child's mastery map, as the child sees it. */
    public List<ChapterMasteryDto> childMastery(String parentEmail, Long studentId) {
        User child = requireOwnChild(parentEmail, studentId);
        return masteryService.forStudent(child.getEmail());
    }

    public List<ProgressPointDto> childProgress(String parentEmail, Long studentId) {
        User child = requireOwnChild(parentEmail, studentId);
        return quizAttemptService.getProgress(child.getEmail());
    }

    /**
     * The result of one of the child's attempts. The attempt id must be among the child's own attempts:
     * a foreign id and a made-up id are refused identically, so ids cannot be probed.
     */
    public AttemptResultViewDto childResult(String parentEmail, Long studentId, Long attemptId) {
        User child = requireOwnChild(parentEmail, studentId);
        boolean isTheirs = quizAttemptService.listMyAttempts(child.getEmail()).stream()
                .anyMatch(attempt -> attempt.attemptId().equals(attemptId));
        if (!isTheirs) {
            throw new ParentAccessException("Not your child's attempt");
        }
        return quizAttemptService.getResult(attemptId, child.getEmail());
    }

    private User requireParent(String parentEmail) {
        return userRepository.findByEmail(parentEmail)
                .orElseThrow(() -> new ParentAccessException("Unknown parent account"));
    }

    private User requireOwnChild(String parentEmail, Long studentId) {
        User parent = requireParent(parentEmail);
        return userRepository.findByIdAndParentId(studentId, parent.getId())
                .orElseThrow(() -> new ParentAccessException("Not your child"));
    }
}
