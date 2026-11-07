package ro.mathlms.publisher;

import java.time.Instant;

/** What the "Edituri" page and the test's "Editură" dropdown send and get. */
public final class PublisherDtos {

    private PublisherDtos() {
    }

    /** Create or edit: the name is required, the description optional (the logo has its own upload). */
    public record PublisherRequest(String name, String description) {
    }

    /**
     * One publisher with how many tests name it. {@code logoVersion} changes whenever the logo does, so the page can
     * put it in the logo's URL and the browser never shows an old one.
     */
    public record PublisherDto(Long id, String name, String description, boolean hasLogo, long logoVersion,
                               long quizCount) {
        static PublisherDto from(Publisher p, long quizCount) {
            Instant changed = p.getUpdatedAt();
            return new PublisherDto(p.getId(), p.getName(), p.getDescription(), p.getLogoKey() != null,
                    changed == null ? 0 : changed.toEpochMilli(), quizCount);
        }
    }
}
