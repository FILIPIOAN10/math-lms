package ro.mathlms.content;

/** A chapter as the API returns it. {@code bookId} is read off the LAZY book proxy. */
public record ChapterDto(Long id, Long bookId, String title, String description) {
    public static ChapterDto from(Chapter chapter) {
        return new ChapterDto(chapter.getId(), chapter.getBook().getId(),
                chapter.getTitle(), chapter.getDescription());
    }
}
