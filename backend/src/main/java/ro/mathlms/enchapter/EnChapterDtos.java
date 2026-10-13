package ro.mathlms.enchapter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** What the EN chapter pages send and receive. */
public final class EnChapterDtos {

    private EnChapterDtos() {
    }

    /** Create or edit a chapter (the place in the list moves separately). */
    public record EnChapterRequest(
            @NotBlank @Size(max = EnChapter.MAX_CODE) String code,
            @NotBlank @Size(max = EnChapter.MAX_TITLE) String title,
            @NotNull EnArea area,
            @Size(max = EnChapter.MAX_NOTES) String notes
    ) {
    }

    /** A worked example: the statement and its explained solution. */
    public record ExampleRequest(
            @NotBlank @Size(max = EnChapterExample.MAX_TEXT) String statement,
            @NotBlank @Size(max = EnChapterExample.MAX_TEXT) String solution
    ) {
    }

    /** A chapter in the list, with how much the teacher has put on its page. */
    public record EnChapterSummaryDto(Long id, String code, String title, EnArea area, int position, long photoCount,
                                      long exampleCount) {
        static EnChapterSummaryDto of(EnChapter c, long photos, long examples) {
            return new EnChapterSummaryDto(c.getId(), c.getCode(), c.getTitle(), c.getArea(), c.getPosition(), photos,
                    examples);
        }
    }

    /** One photographed page of theory; its URL is built from the ids (a photo never changes). */
    public record PhotoDto(Long id) {
    }

    /** A worked example; {@code image} is a version token for the figure's URL, or null when it has none. */
    public record ExampleDto(Long id, String statement, String solution, String image) {
        static ExampleDto from(EnChapterExample e) {
            return new ExampleDto(e.getId(), e.getStatement(), e.getSolution(), e.getImageKey());
        }
    }

    /** A whole page, as the student reads it and the teacher edits it. */
    public record EnChapterPageDto(Long id, String code, String title, EnArea area, String notes, List<PhotoDto> photos,
                                   List<ExampleDto> examples) {
    }

    /** Which way a chapter moves in the list. */
    public enum Direction {
        UP,
        DOWN
    }
}
