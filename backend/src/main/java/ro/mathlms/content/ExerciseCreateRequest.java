package ro.mathlms.content;

import jakarta.validation.constraints.NotBlank;

/** Create payload for an exercise. The owning chapter comes from the path. */
public record ExerciseCreateRequest(
        @NotBlank String statement,
        String solution,
        Difficulty difficulty
) {
}
