package ro.mathlms.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a book. The owning class comes from the path, not the body. */
public record BookRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description
) {
}
