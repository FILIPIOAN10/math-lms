package ro.mathlms.quiz;

import java.time.Instant;
import java.util.List;

/**
 * Student-facing quiz DTOs. Unlike {@link QuizDtos} (the admin view), these deliberately
 * <strong>omit the correct answer and the barem</strong>: an option carries no {@code correct}
 * flag and an item carries no {@code solution}. This is the anti-cheat boundary — the right
 * answer never leaves the server before the attempt is submitted and graded.
 */
public final class StudentQuizDtos {

    private StudentQuizDtos() {
    }

    /** One answer choice as the student sees it — no {@code correct} flag. */
    public record StudentOptionDto(Long id, int position, String text) {
        public static StudentOptionDto from(QuizOption option) {
            return new StudentOptionDto(option.getId(), option.getPosition(), option.getText());
        }
    }

    /** One item as the student sees it — no barem/solution. Options empty for OPEN items. */
    public record StudentItemDto(
            Long id,
            int position,
            QuizItemType type,
            String statement,
            int points,
            List<StudentOptionDto> options
    ) {
        public static StudentItemDto from(QuizItem item, List<QuizOption> options) {
            return new StudentItemDto(
                    item.getId(),
                    item.getPosition(),
                    item.getType(),
                    item.getStatement(),
                    item.getPoints(),
                    options.stream().map(StudentOptionDto::from).toList());
        }
    }

    /** A published quiz ready to be taken. */
    public record StudentQuizDto(
            Long id,
            String title,
            String description,
            Integer timeLimitMinutes, // null = untimed
            List<StudentItemDto> items
    ) {
        public static StudentQuizDto of(Quiz quiz, List<StudentItemDto> items) {
            return new StudentQuizDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                    quiz.getTimeLimitMinutes(), items);
        }
    }

    /**
     * What the student already saved on an in-progress attempt, so a resumed page can restore it.
     * Only the student's own choice (option id / "photo sent") — never whether it is correct.
     */
    public record SavedAnswerDto(Long itemId, Long selectedOptionId, boolean photoUploaded) {
        public static SavedAnswerDto from(ItemResponse response) {
            return new SavedAnswerDto(
                    response.getItem().getId(),
                    response.getSelectedOption() == null ? null : response.getSelectedOption().getId(),
                    response.getImageKey() != null);
        }
    }

    /**
     * The attempt the student is now working on, the answer-hidden quiz to fill in, and the answers
     * already saved (empty for a fresh attempt).
     */
    public record StartedAttemptDto(Long attemptId, QuizAttemptStatus status, StudentQuizDto quiz,
                                    List<SavedAnswerDto> answers,
                                    Instant deadlineAt, // null = untimed
                                    Instant serverNow   // the server's clock, so the browser can show the right countdown despite a skewed own clock
    ) {
    }

    /** One point of the progress chart: how a graded attempt went, as points and as a percent of the quiz's max. */
    public record ProgressPointDto(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Instant submittedAt,
            int score,
            int maxScore,
            int percent
    ) {
    }

    /** One row of the student's own attempt history ("Încercările mele"). */
    public record MyAttemptDto(
            Long attemptId,
            Long quizId,
            String quizTitle,
            QuizAttemptStatus status,
            Instant startedAt,
            Instant submittedAt,
            Integer score
    ) {
        public static MyAttemptDto from(QuizAttempt attempt) {
            return new MyAttemptDto(attempt.getId(), attempt.getQuiz().getId(), attempt.getQuiz().getTitle(),
                    attempt.getStatus(), attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getScore());
        }
    }

    /**
     * Outcome of submitting. {@code autoScore}/{@code autoMaxScore} cover the auto-graded
     * single-choice items; {@code finalScore} is non-null only once the whole attempt is graded
     * (i.e. the quiz had no open items awaiting a teacher).
     */
    public record AttemptResultDto(
            Long attemptId,
            QuizAttemptStatus status,
            int autoScore,
            int autoMaxScore,
            Integer finalScore
    ) {
    }

    /**
     * One item as shown on the student's result view (after submit). Now that grading is done the
     * correct option and the barem — hidden before submit — are revealed for learning.
     */
    public record ItemResultDto(
            int position,
            QuizItemType type,
            String statement,
            int points,
            Integer awardedPoints,
            Boolean correct,
            String selectedOptionText,
            String correctOptionText,
            String barem,
            boolean photoUploaded
    ) {}

    /** The student's graded attempt: the score plus a per-item breakdown. */
    public record AttemptResultViewDto(
            Long attemptId,
            String quizTitle,
            QuizAttemptStatus status,
            Integer finalScore,
            int maxScore,
            List<ItemResultDto> items
    ) {}
}
