package ro.mathlms.ingest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
                              Instant finishedAt, List<ExtractedItem> items) {
    }

    /**
     * The reviewed questions, as the quiz builder takes them (positions are assigned in order). For a grilă the teacher
     * has marked the one correct option; the barem goes in {@code solution}.
     */
    public record CreateQuizFromImportRequest(
            @NotBlank @Size(max = 200) String title,
            Long schoolClassId,
            Long chapterId,
            @NotEmpty @Size(max = ExtractedItems.MAX_ITEMS) List<@Valid ItemRequest> items
    ) {
    }

    public record CreatedQuizDto(Long quizId) {
    }
}
