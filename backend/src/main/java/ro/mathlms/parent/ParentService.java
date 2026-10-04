package ro.mathlms.parent;

import org.springframework.stereotype.Service;
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

    public ParentService(UserRepository userRepository, QuizAttemptService quizAttemptService) {
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
