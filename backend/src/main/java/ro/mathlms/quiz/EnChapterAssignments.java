package ro.mathlms.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The EN chapters the teacher keeps from the AI's proposals, for several questions of one test at once. For each
 * question listed, {@code enChapterId} is its chapter afterwards (null = automatic, by its place on the paper); of its
 * requirements, only those listed change.
 */
public record EnChapterAssignments(@NotNull @Size(max = 200) List<@Valid ItemAssignment> items) {

    public record ItemAssignment(@NotNull Long itemId, Long enChapterId,
                                 @Size(max = QuizItemPart.MAX_PARTS) List<@Valid PartAssignment> parts) {
    }

    public record PartAssignment(@NotNull Long partId, Long enChapterId) {
    }
}
