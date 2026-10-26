package ro.mathlms.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The barem a teacher kept after reviewing what the AI read from the official barem's photos: per question, the right
 * option of a grilă, the barem of an open question and of its requirements. A null field leaves that part as it is.
 */
public final class BaremApply {

    /** Long enough for the longest EN barem of a requirement, with its LaTeX. */
    public static final int MAX_BAREM = 20_000;

    private BaremApply() {
    }

    public record Request(@NotNull @Size(min = 1, max = 100) List<@Valid ItemBarem> items) {
    }

    public record ItemBarem(@NotNull Long itemId, Long correctOptionId, @Size(max = MAX_BAREM) String barem,
                            @Size(max = QuizItemPart.MAX_PARTS) List<@Valid PartBarem> parts) {
    }

    /** As long as the editor allows for a requirement's barem ({@code PartDtos.PartRequest}). */
    public record PartBarem(@NotNull Long partId, @Size(max = 4000) String barem) {
    }
}
