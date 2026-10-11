package ro.mathlms.content;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CRUD for school classes. Writes are transactional (and journalled); uniqueness of name is checked up front. */
@Service
public class SchoolClassService {

    private final SchoolClassRepository repository;
    private final AuditLog auditLog;

    public SchoolClassService(SchoolClassRepository repository, AuditLog auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    public List<SchoolClass> list() {
        return repository.findAll(Sort.by("name")).stream()
                .sorted(Comparator.comparing(SchoolClass::getName, NaturalOrder.BY_NAME)).toList();
    }

    public SchoolClass get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", id));
    }

    @Transactional
    public SchoolClass create(String name, String description) {
        if (repository.existsByName(name)) {
            throw new DuplicateContentException("A class named '" + name + "' already exists");
        }
        SchoolClass saved = repository.save(new SchoolClass(name, description));
        auditLog.record(AuditEvent.of(AuditAction.CLASS_CREATED, saved.getId(), saved.getName()));
        return saved;
    }

    @Transactional
    public SchoolClass update(Long id, String name, String description) {
        SchoolClass schoolClass = get(id);
        if (!schoolClass.getName().equals(name) && repository.existsByName(name)) {
            throw new DuplicateContentException("A class named '" + name + "' already exists");
        }
        String oldName = schoolClass.getName();
        schoolClass.update(name, description);
        SchoolClass saved = repository.save(schoolClass);
        auditLog.record(AuditEvent.of(AuditAction.CLASS_UPDATED, id, schoolClass.getName())
                .details(AuditText.renamedFrom(oldName, schoolClass.getName())));
        return saved;
    }

    /**
     * Books and class-only quizzes block the delete (they are content the teacher would lose);
     * enrollments do not — they only say who attends the class, so they go with it. Homework
     * for the class is removed by the database (ON DELETE CASCADE).
     */
    @Transactional
    public void delete(Long id) {
        SchoolClass schoolClass = get(id);
        long books = repository.countBooks(id);
        long quizzes = repository.countQuizzes(id);
        if (books > 0 || quizzes > 0) {
            List<String> blockers = new ArrayList<>();
            List<String> fixes = new ArrayList<>();
            if (books > 0) {
                blockers.add(RoCount.of(books, "carte", "cărți"));
                fixes.add("Șterge întâi cărțile.");
            }
            if (quizzes > 0) {
                blockers.add(RoCount.of(quizzes, "quiz destinat ei", "quiz-uri destinate ei"));
                fixes.add("Quiz-urile le ștergi sau le muți la „Toți elevii” din pagina Quiz-uri.");
            }
            throw new ContentInUseException("Clasa „" + schoolClass.getName() + "” nu poate fi ștearsă: are "
                    + String.join(" și ", blockers) + ". " + String.join(" ", fixes));
        }
        repository.deleteEnrollments(id);
        repository.deleteById(id);
        auditLog.record(AuditEvent.of(AuditAction.CLASS_DELETED, id, schoolClass.getName()));
    }
}
