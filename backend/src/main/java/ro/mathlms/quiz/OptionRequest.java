package ro.mathlms.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One answer choice when authoring a single-choice item; {@code mistake} is what a wrong one gives away (optional). */
public record OptionRequest(
        int position,
        @NotBlank String text,
        boolean correct,
        @Size(max = QuizOption.MAX_MISTAKE) String mistake
) {
    /** An option without an explanation (what the builder sent before V47). */
    public OptionRequest(int position, String text, boolean correct) {
        this(position, text, correct, null);
    }
}
