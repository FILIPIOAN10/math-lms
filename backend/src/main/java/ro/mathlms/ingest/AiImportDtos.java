package ro.mathlms.ingest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ro.mathlms.quiz.ItemRequest;

import java.time.Instant;
import java.util.List;

/** What the AI import endpoints send and take. */
public final class AiImportDtos {

    private AiImportDtos() {
    }

    /** Whether the button shows, and how much of today's allowance is left. */
    public record AiImportSettingsDto(boolean enabled, int maxPages, int dailyPageLimit, long pagesUsedToday) {
    }

    /**
     * One import. {@code items} only on the detail view (null in the list); {@code pagesAvailable} false once the page
     * files are gone (quiz made, or expired), when "try again" is no longer possible.
     */
    public record AiImportDto(Long id, AiImportStatus status, int pageCount, boolean pagesAvailable, Integer itemCount,
                              String error, Long schoolClassId, Long chapterId, Long quizId, Instant createdAt,
                              Instant finishedAt, List<ExtractedItem> items,
                              Long reusedFrom) { // the import whose reading was copied (no AI call); null = read by the AI
    }

    /**
     * The reviewed questions, as the quiz builder takes them (positions are assigned in order). For a grilă the teacher
     * has marked the one correct option; the barem goes in {@code solution}. {@code figures}: the drawings to cut out
     * of the page photos for some of those questions (as the AI found them, or as the teacher adjusted them); null or
     * empty = none.
     */
    public record CreateQuizFromImportRequest(
            @NotBlank @Size(max = 200) String title,
            Long schoolClassId,
            Long chapterId,
            @NotEmpty @Size(max = ExtractedItems.MAX_ITEMS) List<@Valid ItemRequest> items,
            @Size(max = ExtractedItems.MAX_ITEMS) List<@Valid FigureCrop> figures
    ) {

        /** Without figures: the request as it was before they were cut automatically. */
        public CreateQuizFromImportRequest(String title, Long schoolClassId, Long chapterId, List<ItemRequest> items) {
            this(title, schoolClassId, chapterId, items, null);
        }
    }

    /**
     * The picture of {@code items[index]}: the {@code box} ({@code [ymin, xmin, ymax, xmax]}, 0-1000) of page
     * {@code page} (1-based).
     */
    public record FigureCrop(
            @NotNull @Min(0) Integer index,
            @NotNull @Min(1) Integer page,
            @NotNull @Size(min = 4, max = 4) List<@NotNull @Min(0) @Max(1000) Integer> box
    ) {
    }

    public record CreatedQuizDto(Long quizId) {
    }
}
