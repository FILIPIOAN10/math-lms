package ro.mathlms.quiz;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** A teacher's manual score for one OPEN item. */
public record GradeRequestDto(
        @NotNull(message = "Punctajul este obligatoriu")
        @Min(value = 0, message = "Punctajul nu poate fi negativ")
        Integer points
) {}
