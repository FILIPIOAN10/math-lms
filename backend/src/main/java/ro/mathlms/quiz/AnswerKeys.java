package ro.mathlms.quiz;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a student may see a test's answer key - the right option of each grilă, the numeric answers, the barem of each
 * question and requirement, the note on a wrong option - on their result (and their parent's), in the daily review and
 * in a chapter's practice. Only once they cannot hand the test in again (decided 2026-11-10): while a retake is left the
 * key would be copied into it, and the highest grade counts. A test open for practice shows it anyway - each practice
 * answer gives it away.
 * <p>
 * "A retake left" is the rule the start button follows ({@link AttemptAllowance#retakeLeft()}): the test's limit plus the
 * student's "+1 încercare", counted from the last homework that gave the test to them; no limit = always one left. So a
 * "+1 încercare" or a catch-up redo hides the key again until that attempt is handed in.
 * <p>
 * Two queries for any number of students (their grants, their homework), the attempts are the caller's.
 */
public class AnswerKeys {

    private final QuizRepository quizRepository;

    public AnswerKeys(QuizRepository quizRepository) {
        this.quizRepository = quizRepository;
    }

    /**
     * For each student among {@code attempts}, the quizzes whose key they may not see yet. {@code attempts} must be all
     * of each student's attempts at each quiz that matters (any mode, any status), with the quiz loaded. A student or
     * quiz without attempts here is not in the answer: nothing of theirs to hide.
     */
    public Map<Long, Set<Long>> hiddenQuizzes(Collection<QuizAttempt> attempts) {
        Map<Long, Map<Long, List<QuizAttempt>>> byStudent = attempts.stream()
                .filter(a -> !a.getQuiz().isPracticeAllowed()) // a practice gives the key away anyway
                .collect(Collectors.groupingBy(a -> a.getStudent().getId(),
                        Collectors.groupingBy(a -> a.getQuiz().getId())));
        if (byStudent.isEmpty()) {
            return Map.of();
        }
        Set<Long> studentIds = byStudent.keySet();
        Map<Long, Map<Long, Integer>> extra = new HashMap<>();
        for (AttemptGrant grant : quizRepository.findAttemptGrantsOf(studentIds)) {
            extra.computeIfAbsent(grant.getStudent().getId(), id -> new HashMap<>())
                    .put(grant.getQuiz().getId(), grant.getExtra());
        }
        Map<Long, Map<Long, Instant>> homework = new HashMap<>();
        for (StudentHomeworkGiven given : quizRepository.findHomeworkGivenTo(studentIds)) {
            homework.computeIfAbsent(given.studentId(), id -> new HashMap<>()).put(given.quizId(), given.givenAt());
        }
        Map<Long, Set<Long>> hidden = new HashMap<>();
        byStudent.forEach((studentId, byQuiz) -> {
            Set<Long> quizzes = new HashSet<>();
            byQuiz.forEach((quizId, atQuiz) -> {
                Quiz quiz = atQuiz.get(0).getQuiz();
                Integer allowed = AttemptGrant.allowed(quiz.getMaxAttempts(),
                        extra.getOrDefault(studentId, Map.of()).getOrDefault(quizId, 0));
                Instant givenAt = homework.getOrDefault(studentId, Map.of()).get(quizId);
                if (AttemptAllowance.of(allowed, atQuiz, givenAt).retakeLeft()) {
                    quizzes.add(quizId);
                }
            });
            hidden.put(studentId, quizzes);
        });
        return hidden;
    }

    /** Whether the student of {@code attemptsAtQuiz} may not see the key of {@code quiz} yet. */
    public boolean hidden(Quiz quiz, List<QuizAttempt> attemptsAtQuiz) {
        return hiddenQuizzes(attemptsAtQuiz).values().stream().anyMatch(quizzes -> quizzes.contains(quiz.getId()));
    }
}
