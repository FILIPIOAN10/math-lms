package ro.mathlms.content;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GradeRequestDto(
        @NotNull(message = "Punctajul este obligatoriu")
        @Min(value = 0, message = "Punctajul nu poate fi negativ")
        Integer points
) {}
