package ro.mathlms.quiz;

import jakarta.validation.constraints.NotBlank;

/** One answer choice when authoring a single-choice item. */
public record OptionRequest(
        int position,
        @NotBlank String text,
        boolean correct
) {
}
