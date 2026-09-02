package ro.mathlms.content;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for books within a school class. */
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final SchoolClassRepository schoolClassRepository;

    public BookService(BookRepository bookRepository, SchoolClassRepository schoolClassRepository) {
        this.bookRepository = bookRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    public List<Book> listByClass(Long classId) {
        requireClass(classId);
        return bookRepository.findBySchoolClassIdOrderByTitle(classId);
    }

    public Book get(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Book", id));
    }

    @Transactional
    public Book create(Long classId, String title, String description) {
        SchoolClass schoolClass = requireClass(classId);
        if (bookRepository.existsBySchoolClassIdAndTitle(classId, title)) {
            throw new DuplicateContentException(
                    "A book titled '" + title + "' already exists in this class");
        }
        return bookRepository.save(new Book(schoolClass, title, description));
    }

    @Transactional
    public Book update(Long id, String title, String description) {
        Book book = get(id);
        boolean titleChanged = !book.getTitle().equals(title);
        if (titleChanged
                && bookRepository.existsBySchoolClassIdAndTitle(book.getSchoolClass().getId(), title)) {
            throw new DuplicateContentException(
                    "A book titled '" + title + "' already exists in this class");
        }
        book.update(title, description);
        return bookRepository.save(book);
    }

    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ContentNotFoundException("Book", id);
        }
        bookRepository.deleteById(id);
    }

    private SchoolClass requireClass(Long classId) {
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", classId));
    }
}
