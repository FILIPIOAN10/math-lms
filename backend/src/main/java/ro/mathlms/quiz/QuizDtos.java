package ro.mathlms.quiz;

import ro.mathlms.content.SchoolClass;

import java.util.List;

/** Response DTOs for the admin quiz-builder. Grouped in one file as they are small and related. */
public final class QuizDtos {

    private QuizDtos() {
    }

    /** A quiz in a list (no items). */
    public record QuizSummaryDto(Long id, String title, String description, QuizStatus status,
                                 Long schoolClassId, String schoolClassName, Integer timeLimitMinutes,
                                 boolean practiceAllowed, Long chapterId) {
        public static QuizSummaryDto from(Quiz quiz) {
            SchoolClass schoolClass = quiz.getSchoolClass(); // null = for every student
            return new QuizSummaryDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(), quiz.getStatus(),
                    schoolClass == null ? null : schoolClass.getId(),
                    schoolClass == null ? null : schoolClass.getName(), quiz.getTimeLimitMinutes(),
                    quiz.isPracticeAllowed(), quiz.getChapter() == null ? null : quiz.getChapter().getId());
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
            List<OptionDto> options,
            List<String> hints, // progressive hints in order (the builder view; students get them one at a time)
            Long chapterId, // its own chapter; null = the quiz's
            String section, // the heading it sits under ("SUBIECTUL I"); null = none
            String image, // the picture under the statement: a version token for its URL, or null
            boolean imageRequired, // imported with "[imagine]" and still waiting for its picture
            List<PartDtos.PartDto> parts // requirements a), b)... of an open problem; empty = none
    ) {
        public static ItemDto from(QuizItem item, List<QuizOption> options, List<QuizItemHint> hints) {
            return from(item, options, hints, List.of());
        }

        public static ItemDto from(QuizItem item, List<QuizOption> options, List<QuizItemHint> hints,
                                   List<QuizItemPart> parts) {
            return new ItemDto(
                    item.getId(),
                    item.getPosition(),
                    item.getType(),
                    item.getStatement(),
                    item.getPoints(),
                    item.getSolution(),
                    options.stream().map(OptionDto::from).toList(),
                    hints.stream().map(QuizItemHint::getText).toList(),
                    item.getChapter() == null ? null : item.getChapter().getId(),
                    item.getSection(),
                    item.getImageKey(),
                    item.isImageRequired(),
                    PartDtos.PartDto.of(parts));
        }
    }

    /** A quiz with all its items (the builder view). */
    public record QuizDetailDto(
            Long id,
            String title,
            String description,
            QuizStatus status,
            List<ItemDto> items,
            Long chapterId // the default its items inherit
    ) {
        public static QuizDetailDto of(Quiz quiz, List<ItemDto> items) {
            return new QuizDetailDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(),
                    quiz.getStatus(), items, quiz.getChapter() == null ? null : quiz.getChapter().getId());
        }
    }
}
