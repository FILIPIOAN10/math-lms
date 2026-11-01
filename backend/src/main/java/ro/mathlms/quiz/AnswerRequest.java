package ro.mathlms.quiz;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A student's answer to one question: the picked option's id, or {@code value}, the number typed for a numeric one
 * (blank takes it back). {@code clientId} (one per browser and attempt) and {@code clientSeq} (that browser's counter)
 * let the server ignore an older retry that arrives late; both optional.
 */
public record AnswerRequest(
        Long optionId,
        UUID clientId,
        @PositiveOrZero Long clientSeq,
        @Size(max = NumericAnswer.MAX_LENGTH) String value
) {
    public AnswerRequest(Long optionId) {
        this(optionId, null, null, null);
    }

    public AnswerRequest(Long optionId, UUID clientId, Long clientSeq) {
        this(optionId, clientId, clientSeq, null);
    }
}
