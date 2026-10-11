package ro.mathlms.content;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;

import java.util.Comparator;
import java.util.List;

/** CRUD for chapters within a book (writes are journalled). */
@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final BookRepository bookRepository;
    private final AuditLog auditLog;

    public ChapterService(ChapterRepository chapterRepository, BookRepository bookRepository, AuditLog auditLog) {
        this.chapterRepository = chapterRepository;
        this.bookRepository = bookRepository;
        this.auditLog = auditLog;
    }

    /** "Funcții (Matematică M1)": a chapter named with its book, as the journal shows it. */
    private static String label(Chapter chapter) {
        return chapter.getTitle() + " (" + chapter.getBook().getTitle() + ")";
    }

    public List<Chapter> listByBook(Long bookId) {
        requireBook(bookId);
        return chapterRepository.findByBookIdOrderByTitle(bookId).stream()
                .sorted(Comparator.comparing(Chapter::getTitle, NaturalOrder.BY_NAME)).toList();
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
        Chapter saved = chapterRepository.save(new Chapter(book, title, description));
        auditLog.record(AuditEvent.of(AuditAction.CHAPTER_CREATED, saved.getId(), label(saved)));
        return saved;
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
        String oldTitle = chapter.getTitle();
        chapter.update(title, description);
        Chapter saved = chapterRepository.save(chapter);
        auditLog.record(AuditEvent.of(AuditAction.CHAPTER_UPDATED, id, label(chapter))
                .details(AuditText.renamedFrom(oldTitle, chapter.getTitle())));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Chapter chapter = chapterRepository.findById(id).orElseThrow(() -> new ContentNotFoundException("Chapter", id));
        long exercises = chapterRepository.countExercises(id);
        if (exercises > 0) {
            throw new ContentInUseException("Capitolul „" + chapter.getTitle() + "” nu poate fi șters: are "
                    + RoCount.of(exercises, "exercițiu", "exerciții") + ". Șterge întâi exercițiile.");
        }
        chapterRepository.deleteById(id);
        auditLog.record(AuditEvent.of(AuditAction.CHAPTER_DELETED, id, label(chapter)));
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ContentNotFoundException("Book", bookId));
    }
}
