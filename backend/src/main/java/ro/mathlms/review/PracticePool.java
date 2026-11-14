package ro.mathlms.review;

import ro.mathlms.quiz.AnswerKeys;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.User;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which tests a student may practise questions of, outside the test itself: published tests they can see that allow
 * practice, or that they handed in and cannot hand in again (their result already shows the answers, see
 * {@link AnswerKeys}) - never one with a test of theirs still running on it. Shared by a chapter's practice and by
 * „Dă-mi una asemănătoare", so neither can give away a test still to be written.
 */
final class PracticePool {

    private final QuizAttemptService quizAttemptService;
    private final AnswerKeys answerKeys;

    PracticePool(QuizAttemptService quizAttemptService, QuizRepository quizRepository) {
        this.quizAttemptService = quizAttemptService;
        this.answerKeys = new AnswerKeys(quizRepository);
    }

    /** The ids of those tests; {@code onlyQuizId} = just that one, if it qualifies (checking one answer). */
    Set<Long> quizIds(User student, List<QuizAttempt> attempts, Long onlyQuizId) {
        Set<Long> handedIn = attempts.stream()
                .filter(a -> a.getMode() == AttemptMode.TEST && a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        Set<Long> running = attempts.stream()
                .filter(a -> a.getMode() == AttemptMode.TEST && a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                .map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        Set<Long> keyHidden = answerKeys.hiddenQuizzes(attempts).getOrDefault(student.getId(), Set.of());
        return quizAttemptService.listPublished(student.getEmail()).stream()
                .filter(q -> onlyQuizId == null || onlyQuizId.equals(q.getId()))
                .filter(q -> !running.contains(q.getId()))
                .filter(q -> q.isPracticeAllowed() || (handedIn.contains(q.getId()) && !keyHidden.contains(q.getId())))
                .map(Quiz::getId)
                .collect(Collectors.toSet());
    }
}
