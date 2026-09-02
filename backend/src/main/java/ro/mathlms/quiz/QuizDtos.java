package ro.mathlms.quiz;

import java.util.List;

/** Response DTOs for the admin quiz-builder. Grouped in one file as they are small and related. */
public final class QuizDtos {

    private QuizDtos() {
    }

    /** A quiz in a list (no items). */
    public record QuizSummaryDto(Long id, String title, String description, QuizStatus status) {
        public static QuizSummaryDto from(Quiz quiz) {
            return new QuizSummaryDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(), quiz.getStatus());
        }
    }

    /** One answer choice. {@code correct} is exposed here because this is the ADMIN view. */
    public record OptionDto(Long id, int position, String text, boolean correct) {
        public static OptionDto from(QuizOption option) {
            return new OptionDto(option.getId(), option.getPosition(), option.getText(), option.isCorrect());
        }
    }

    /** One item with its options (empty for OPEN items). */
    public record ItemDto(
            Long id,
            int position,
            QuizItemType type,
            String statement,
            int points,
            String solution,
            List<OptionDto> options
    ) {
        public static ItemDto from(QuizItem item, List<QuizOption> options) {
            return new ItemDto(
                    item.getId(),
                    item.getPosition(),
                    item.getType(),
                    item.getStatement(),
                    item.getPoints(),
                    item.getSolution(),
                    options.stream().map(OptionDto::from).toList());
        }
    }

    /** A quiz with all its items (the builder view). */
    public record QuizDetailDto(
            Long id,
            String title,
            String description,
            QuizStatus status,
            List<ItemDto> items
    ) {
        public static QuizDetailDto of(Quiz quiz, List<ItemDto> items) {
            return new QuizDetailDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                    quiz.getStatus(), items);
        }
    }
}
