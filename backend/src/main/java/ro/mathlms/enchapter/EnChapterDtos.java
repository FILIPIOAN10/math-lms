package ro.mathlms.enchapter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** What the EN chapter pages send and receive. */
public final class EnChapterDtos {

    private EnChapterDtos() {
    }

    /**
     * Create or edit a chapter (the place in the list moves separately). {@code parentId} makes it a section of that
     * chapter (or a point of that section); {@code bookPage} is the page printed in the book where it starts.
     */
    public record EnChapterRequest(
            @NotBlank @Size(max = EnChapter.MAX_CODE) String code,
            @NotBlank @Size(max = EnChapter.MAX_TITLE) String title,
            @NotNull EnArea area,
            // No @Size here: the entity refuses a too long text with a sentence the teacher can read.
            String notes,
            Long parentId,
            @Min(1) @Max(EnChapter.MAX_BOOK_PAGE) Integer bookPage
    ) {
    }

    /** A worked example: the statement and its explained solution. */
    public record ExampleRequest(
            @NotBlank @Size(max = EnChapterExample.MAX_TEXT) String statement,
            @NotBlank @Size(max = EnChapterExample.MAX_TEXT) String solution
    ) {
    }

    /** A chapter in the list, with how much the teacher has put on its page. */
    public record EnChapterSummaryDto(Long id, String code, String title, EnArea area, int position, Long parentId,
                                      Integer bookPage, long photoCount, long exampleCount) {
        static EnChapterSummaryDto of(EnChapter c, long photos, long examples) {
            return new EnChapterSummaryDto(c.getId(), c.getCode(), c.getTitle(), c.getArea(), c.getPosition(),
                    c.getParentId(), c.getBookPage(), photos, examples);
        }
    }

    /**
     * A section or a point under a chapter: the page of the book it starts on, and its written theory ({@code notes},
     * null when none yet) - so the chapter page can show the whole chapter as text, section by section.
     */
    public record SectionDto(Long id, String code, String title, Long parentId, Integer bookPage, String notes) {
        static SectionDto of(EnChapter c) {
            return new SectionDto(c.getId(), c.getCode(), c.getTitle(), c.getParentId(), c.getBookPage(), c.getNotes());
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

    /**
     * A whole page, as the student reads it and the teacher edits it. {@code rootId} is the chapter whose pages
     * (photos) hold this one - itself for a chapter; {@code sections} is everything under it, in book order.
     */
    public record EnChapterPageDto(Long id, String code, String title, EnArea area, String notes, Long parentId,
                                   Long rootId, Integer bookPage, List<SectionDto> sections, List<PhotoDto> photos,
                                   List<ExampleDto> examples) {
    }

    /** Which way a chapter moves in the list. */
    public enum Direction {
        UP,
        DOWN
    }
}
