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
            List<StudentOptionDto> options,
            int hintCount // hints this item has - only ever > 0 in practice mode; a graded test hides even their existence
    ) {
        public static StudentItemDto from(QuizItem item, List<QuizOption> options, int hintCount) {
            return new StudentItemDto(
                    item.getId(),
                    item.getPosition(),
                    item.getType(),
                    item.getStatement(),
                    item.getPoints(),
                    options.stream().map(StudentOptionDto::from).toList(),
                    hintCount);
        }
    }

    /** A published quiz ready to be taken. */
    /**
     * What a student sees BEFORE starting: enough to know what they are walking into (how many items, how many
     * points, whether a clock will run) without revealing a single statement.
     */
    public record QuizPreviewDto(
            Long id,
            String title,
            String description,
            Integer timeLimitMinutes, // null = untimed
            boolean practiceAllowed,
            int itemCount,
            int maxScore
    ) {}

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
     * What a PRACTICE answer reveals at once: whether it was right ({@code null} for an open item, which a teacher
     * would mark), the id of the right option, and the barem. Never produced for a graded TEST.
     */
    public record AnswerFeedbackDto(Boolean correct, Long correctOptionId, String solution) {
    }

    /** One revealed hint: its number (1-based), its text, and how many hints the item has in all. */
    public record HintDto(int number, String text, int total) {
    }

    /** The hints a resumed practice has already revealed for one item, in order. */
    public record RevealedHintsDto(Long itemId, List<String> hints) {
    }

    /**
     * What the student already saved on an in-progress attempt, so a resumed page can restore it.
     * Only the student's own choice (option id / "photo sent") — never whether it is correct.
     */
    public record SavedAnswerDto(Long itemId, Long selectedOptionId, boolean photoUploaded,
                                 AnswerFeedbackDto feedback // PRACTICE only; always null in a graded TEST
    ) {
        public static SavedAnswerDto from(ItemResponse response) {
            return from(response, null);
        }

        public static SavedAnswerDto from(ItemResponse response, AnswerFeedbackDto feedback) {
            return new SavedAnswerDto(
                    response.getItem().getId(),
                    response.getSelectedOption() == null ? null : response.getSelectedOption().getId(),
                    response.getImageKey() != null,
                    feedback);
        }
    }

    /**
     * The attempt the student is now working on, the answer-hidden quiz to fill in, and the answers
     * already saved (empty for a fresh attempt).
     */
    public record StartedAttemptDto(Long attemptId, QuizAttemptStatus status, StudentQuizDto quiz,
                                    List<SavedAnswerDto> answers,
                                    Instant deadlineAt, // null = untimed
                                    Instant serverNow,  // the server's clock, so the browser can show the right countdown despite a skewed own clock
                                    AttemptMode mode,
                                    List<RevealedHintsDto> revealedHints // practice only; empty in a graded test
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
            Integer score,
            AttemptMode mode
    ) {
        public static MyAttemptDto from(QuizAttempt attempt) {
            return new MyAttemptDto(attempt.getId(), attempt.getQuiz().getId(), attempt.getQuiz().getTitle(),
                    attempt.getStatus(), attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getScore(),
                    attempt.getMode());
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
            Long itemId,        // lets the student's page fetch their own photo of an OPEN item
            int position,
            QuizItemType type,
            String statement,
            int points,
            Integer awardedPoints,
            Boolean correct,
            String selectedOptionText,
            String correctOptionText,
            String barem,
            boolean photoUploaded,
            int hintsUsed,      // practice only; 0 in a graded test
            int hintsAvailable  // practice only; 0 in a graded test
    ) {}

    /** The student's graded attempt: the score plus a per-item breakdown. */
    public record AttemptResultViewDto(
            Long attemptId,
            String quizTitle,
            QuizAttemptStatus status,
            Integer finalScore,
            int maxScore,
            List<ItemResultDto> items,
            AttemptMode mode
    ) {}
}
