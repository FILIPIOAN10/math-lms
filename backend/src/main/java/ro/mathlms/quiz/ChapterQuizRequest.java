package ro.mathlms.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * T2 "Creează quiz din capitol": the chapter's exercises the teacher ticked, the new quiz's title and how many points
 * each question is worth (the same for all; the teacher adjusts single questions in the editor afterwards).
 */
public record ChapterQuizRequest(
        @NotBlank @Size(max = 200) String title,
        @NotEmpty @Size(max = 50) List<@NotNull Long> exerciseIds,
        @Min(1) @Max(100) int points
) {
}
