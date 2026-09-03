package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentItemDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentQuizDto;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The student side of quizzes: browse published quizzes, start (or resume) an attempt, record
 * single-choice answers, and submit. On submit the SINGLE_CHOICE items are graded automatically
 * on the server; OPEN items are left for a teacher (Faza Q7). The correct answer is never sent to
 * the client before grading — see {@link StudentQuizDtos}.
 */
@Service
public class QuizAttemptService {

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final UserRepository userRepository;

    public QuizAttemptService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                              QuizOptionRepository optionRepository, QuizAttemptRepository attemptRepository,
                              ItemResponseRepository responseRepository, UserRepository userRepository) {
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.userRepository = userRepository;
    }

    /** Published quizzes a student may take. */
    public List<Quiz> listPublished() {
        return quizRepository.findByStatusOrderByTitle(QuizStatus.PUBLISHED);
    }

    /**
     * Starts a fresh attempt at a published quiz, or resumes the student's existing in-progress one,
     * and returns the answer-hidden quiz to fill in.
     */
    @Transactional
    public StartedAttemptDto startAttempt(Long quizId, String studentEmail) {
        User student = requireUser(studentEmail);
        Quiz quiz = quizRepository.findById(quizId)
                .filter(q -> q.getStatus() == QuizStatus.PUBLISHED)
                .orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
        QuizAttempt attempt = attemptRepository
                .findByQuizIdAndStudentIdAndStatus(quizId, student.getId(), QuizAttemptStatus.IN_PROGRESS)
                .orElseGet(() -> attemptRepository.save(new QuizAttempt(quiz, student)));
        return new StartedAttemptDto(attempt.getId(), attempt.getStatus(), studentQuiz(quiz));
    }

    /** Records (or replaces) the student's choice for one SINGLE_CHOICE item. */
    @Transactional
    public void saveResponse(Long attemptId, Long itemId, Long optionId, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            throw new InvalidQuizException("Only single-choice items are answered this way");
        }
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
        if (!option.getItem().getId().equals(itemId)) {
            throw new InvalidQuizException("Option does not belong to this item");
        }
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        response.answerSingleChoice(option);
        responseRepository.save(response);
    }

    /**
     * Hands the attempt in. Every SINGLE_CHOICE response is auto-graded (correct earns the item's
     * points, anything else zero). If the quiz has no OPEN items the attempt is fully graded and its
     * final score set; otherwise it stays SUBMITTED until a teacher scores the open items.
     */
    @Transactional
    public AttemptResultDto submit(Long attemptId, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> byItem = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int autoScore = 0;
        int autoMaxScore = 0;
        boolean hasOpenItems = false;
        for (QuizItem item : items) {
            if (item.getType() != QuizItemType.SINGLE_CHOICE) {
                hasOpenItems = true;
                continue;
            }
            autoMaxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            boolean correct = response != null && response.getSelectedOption() != null
                    && response.getSelectedOption().isCorrect();
            int awarded = correct ? item.getPoints() : 0;
            if (response != null) {
                response.gradeAuto(correct, awarded);
                responseRepository.save(response);
            }
            autoScore += awarded;
        }

        attempt.submit();
        if (!hasOpenItems) {
            attempt.markGraded(autoScore);
        }
        attemptRepository.save(attempt);
        return new AttemptResultDto(attempt.getId(), attempt.getStatus(),
                autoScore, autoMaxScore, attempt.getScore());
    }

    private StudentQuizDto studentQuiz(Quiz quiz) {
        List<StudentItemDto> items = itemRepository.findByQuizIdOrderByPosition(quiz.getId()).stream()
                .map(item -> StudentItemDto.from(item, optionRepository.findByItemIdOrderByPosition(item.getId())))
                .toList();
        return StudentQuizDto.of(quiz, items);
    }

    private QuizAttempt requireOwnedInProgress(Long attemptId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getStudent().getEmail().equals(studentEmail)) {
            throw new QuizAccessException("This attempt belongs to another student");
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("This attempt is no longer in progress");
        }
        return attempt;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new QuizAccessException("Unknown user " + email));
    }
}
