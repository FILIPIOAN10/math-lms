package ro.mathlms.content;

/**
 * A book as the API returns it. {@code schoolClassId} reads the id off the LAZY class
 * proxy, which is safe without initializing it.
 */
public record BookDto(Long id, Long schoolClassId, String title, String description) {
    public static BookDto from(Book book) {
        return new BookDto(book.getId(), book.getSchoolClass().getId(),
                book.getTitle(), book.getDescription());
    }
}
