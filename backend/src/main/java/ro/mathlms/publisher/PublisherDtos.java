package ro.mathlms.publisher;

import java.time.Instant;
import java.util.List;

/** What the "Edituri" pages and the test's "Editură" / "Carte" dropdowns send and get. */
public final class PublisherDtos {

    private PublisherDtos() {
    }

    /** Create or edit: the name is required, the description optional (the logo has its own upload). */
    public record PublisherRequest(String name, String description) {
    }

    /** Create or edit a book: the title is required, the description optional. */
    public record BookRequest(String title, String description) {
    }

    /**
     * One publisher with how many tests name it, and its books. {@code logoVersion} changes whenever the logo does,
     * so the page can put it in the logo's URL and the browser never shows an old one.
     */
    public record PublisherDto(Long id, String name, String description, boolean hasLogo, long logoVersion,
                               long quizCount, List<BookDto> books) {
        static PublisherDto from(Publisher p, long quizCount, List<BookDto> books) {
            Instant changed = p.getUpdatedAt();
            return new PublisherDto(p.getId(), p.getName(), p.getDescription(), p.getLogoKey() != null,
                    changed == null ? 0 : changed.toEpochMilli(), quizCount, books);
        }
    }

    /** One book of a publisher, with how many tests it holds. */
    public record BookDto(Long id, Long publisherId, String title, String description, long quizCount) {
        static BookDto from(PublisherBook b, long quizCount) {
            return new BookDto(b.getId(), b.getPublisher().getId(), b.getTitle(), b.getDescription(), quizCount);
        }
    }
}
