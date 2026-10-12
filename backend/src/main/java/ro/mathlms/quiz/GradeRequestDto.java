package ro.mathlms.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A teacher's manual score for one OPEN item, with an optional written comment for the student. For a problem with
 * requirements a), b)... the points come per requirement in {@code parts} (some may still be missing) and
 * {@code points} is ignored; otherwise {@code points} is required.
 */
public record GradeRequestDto(
        @Min(value = 0, message = "Punctajul nu poate fi negativ")
        Integer points,

        @Size(max = TeacherComment.MAX_LENGTH, message = "Comentariul poate avea cel mult 2000 de caractere")
        String comment,

        @Valid @Size(max = QuizItemPart.MAX_PARTS) List<PartDtos.PartGradeRequest> parts
) {
    public GradeRequestDto(Integer points, String comment) {
        this(points, comment, null);
    }
}
