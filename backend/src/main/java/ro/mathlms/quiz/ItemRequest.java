package ro.mathlms.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create/update payload for a quiz item. For a {@link QuizItemType#SINGLE_CHOICE} item,
 * {@code options} must have at least two entries with exactly one correct (checked in the
 * service); for {@link QuizItemType#OPEN}, {@code options} is ignored.
 */
public record ItemRequest(
        @NotNull QuizItemType type,
        int position,
        @NotBlank String statement,
        @Min(0) int points,
        String solution,
        @Valid List<OptionRequest> options,
        @Size(max = QuizItem.MAX_HINTS) List<@NotBlank @Size(max = 2000) String> hints, // optional progressive hints, in order
        Long chapterId, // optional: overrides the quiz's chapter for this question; null = inherit (E6)
        @Size(max = QuizItem.MAX_SECTION) String section // optional heading it sits under ("SUBIECTUL I"); null = none
) {
    /** An item without a section heading (what the builder sent before EN sections). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints, Long chapterId) {
        this(type, position, statement, points, solution, options, hints, chapterId, null);
    }

    /** An item without hints (what the builder sent before E3). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options) {
        this(type, position, statement, points, solution, options, null, null, null);
    }

    /** An item without its own chapter (what the builder sent before E6). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints) {
        this(type, position, statement, points, solution, options, hints, null, null);
    }
}
