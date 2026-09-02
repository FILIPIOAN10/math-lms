package ro.mathlms.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a school class. */
public record SchoolClassRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description
) {
}
