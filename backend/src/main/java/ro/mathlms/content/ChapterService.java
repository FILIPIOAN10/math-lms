package ro.mathlms.content;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for chapters within a book. */
@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final BookRepository bookRepository;

    public ChapterService(ChapterRepository chapterRepository, BookRepository bookRepository) {
        this.chapterRepository = chapterRepository;
        this.bookRepository = bookRepository;
    }

    public List<Chapter> listByBook(Long bookId) {
        requireBook(bookId);
        return chapterRepository.findByBookIdOrderByTitle(bookId);
    }

    public Chapter get(Long id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Chapter", id));
    }

    @Transactional
    public Chapter create(Long bookId, String title, String description) {
        Book book = requireBook(bookId);
        if (chapterRepository.existsByBookIdAndTitle(bookId, title)) {
            throw new DuplicateContentException(
                    "A chapter titled '" + title + "' already exists in this book");
        }
        return chapterRepository.save(new Chapter(book, title, description));
    }

    @Transactional
    public Chapter update(Long id, String title, String description) {
        Chapter chapter = get(id);
        boolean titleChanged = !chapter.getTitle().equals(title);
        if (titleChanged
                && chapterRepository.existsByBookIdAndTitle(chapter.getBook().getId(), title)) {
            throw new DuplicateContentException(
                    "A chapter titled '" + title + "' already exists in this book");
        }
        chapter.update(title, description);
        return chapterRepository.save(chapter);
    }

    @Transactional
    public void delete(Long id) {
        if (!chapterRepository.existsById(id)) {
            throw new ContentNotFoundException("Chapter", id);
        }
        chapterRepository.deleteById(id);
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ContentNotFoundException("Book", bookId));
    }
}
