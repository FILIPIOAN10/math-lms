package ro.mathlms.quiz;

import jakarta.validation.constraints.NotNull;

/** A student's choice for one single-choice item: the picked option's id. */
public record AnswerRequest(
        @NotNull Long optionId
) {
}
