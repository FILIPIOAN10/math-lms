package ro.mathlms.quiz;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * A student's answer to one question: the picked option's id, or {@code value}, the number typed for a numeric one
 * (blank takes it back), or {@code partValues}, the final results typed for an open problem's requirements, by part id
 * - always the whole set (V72). {@code clientId} (one per browser and attempt) and {@code clientSeq} (that browser's
 * counter) let the server ignore an older retry that arrives late; both optional.
 */
public record AnswerRequest(
        Long optionId,
        UUID clientId,
        @PositiveOrZero Long clientSeq,
        @Size(max = NumericAnswer.MAX_LENGTH) String value,
        @Size(max = QuizItemPart.MAX_PARTS) Map<Long, @Size(max = NumericAnswer.MAX_LENGTH) String> partValues
) {
    public AnswerRequest(Long optionId) {
        this(optionId, null, null, null, null);
    }

    public AnswerRequest(Long optionId, UUID clientId, Long clientSeq) {
        this(optionId, clientId, clientSeq, null, null);
    }

    public AnswerRequest(Long optionId, UUID clientId, Long clientSeq, String value) {
        this(optionId, clientId, clientSeq, value, null);
    }
}
