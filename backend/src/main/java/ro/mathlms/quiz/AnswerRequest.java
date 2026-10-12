package ro.mathlms.quiz;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * A student's choice for one single-choice item: the picked option's id. {@code clientId} (one per browser and attempt)
 * and {@code clientSeq} (that browser's counter) let the server ignore an older retry that arrives late; both optional.
 */
public record AnswerRequest(
        @NotNull Long optionId,
        UUID clientId,
        @PositiveOrZero Long clientSeq
) {
    public AnswerRequest(Long optionId) {
        this(optionId, null, null);
    }
}
