package ro.mathlms.content;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;

import java.util.Comparator;
import java.util.List;

/** CRUD for books within a school class (writes are journalled). */
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AuditLog auditLog;

    public BookService(BookRepository bookRepository, SchoolClassRepository schoolClassRepository, AuditLog auditLog) {
        this.bookRepository = bookRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.auditLog = auditLog;
    }

    /** "Matematică M1 (Clasa a 9-a)": a book named with its class, as the journal shows it. */
    private static String label(Book book) {
        return book.getTitle() + " (" + book.getSchoolClass().getName() + ")";
    }

    public List<Book> listByClass(Long classId) {
        requireClass(classId);
        return bookRepository.findBySchoolClassIdOrderByTitle(classId).stream()
                .sorted(Comparator.comparing(Book::getTitle, NaturalOrder.BY_NAME)).toList();
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
        Book saved = bookRepository.save(new Book(schoolClass, title, description));
        auditLog.record(AuditEvent.of(AuditAction.BOOK_CREATED, saved.getId(), label(saved)));
        return saved;
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
        String oldTitle = book.getTitle();
        book.update(title, description);
        Book saved = bookRepository.save(book);
        auditLog.record(AuditEvent.of(AuditAction.BOOK_UPDATED, id, label(book))
                .details(AuditText.renamedFrom(oldTitle, book.getTitle())));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new ContentNotFoundException("Book", id));
        long chapters = bookRepository.countChapters(id);
        if (chapters > 0) {
            throw new ContentInUseException("Cartea „" + book.getTitle() + "” nu poate fi ștearsă: are "
                    + RoCount.of(chapters, "capitol", "capitole") + ". Șterge întâi capitolele.");
        }
        bookRepository.deleteById(id);
        auditLog.record(AuditEvent.of(AuditAction.BOOK_DELETED, id, label(book)));
    }

    private SchoolClass requireClass(Long classId) {
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", classId));
    }
}
