package ro.mathlms.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Update payload for an exercise. {@code version} is the version the client last read; the
 * server rejects the update with 409 if it no longer matches (someone edited in between).
 */
public record ExerciseUpdateRequest(
        @NotBlank String statement,
        String solution,
        Difficulty difficulty,
        @NotNull Long version
) {
}
