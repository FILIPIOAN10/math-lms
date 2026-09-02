package ro.mathlms.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        @Valid List<OptionRequest> options
) {
}
