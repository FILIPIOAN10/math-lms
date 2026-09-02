package ro.mathlms.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a quiz's own fields (items are managed separately). */
public record QuizRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description
) {
}
