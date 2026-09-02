package ro.mathlms.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a chapter. The owning book comes from the path. */
public record ChapterRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description
) {
}
