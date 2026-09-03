package ro.mathlms.quiz;

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
            List<StudentItemDto> items
    ) {
        public static StudentQuizDto of(Quiz quiz, List<StudentItemDto> items) {
            return new StudentQuizDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(), items);
        }
    }

    /** The attempt the student is now working on, plus the answer-hidden quiz to fill in. */
    public record StartedAttemptDto(Long attemptId, QuizAttemptStatus status, StudentQuizDto quiz) {
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
}
