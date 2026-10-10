package ro.mathlms.quiz;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A teacher's manual score for one OPEN item, with an optional written comment for the student. */
public record GradeRequestDto(
        @NotNull(message = "Punctajul este obligatoriu")
        @Min(value = 0, message = "Punctajul nu poate fi negativ")
        Integer points,

        @Size(max = TeacherComment.MAX_LENGTH, message = "Comentariul poate avea cel mult 2000 de caractere")
        String comment
) {}
