package ro.mathlms.content;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
import ro.mathlms.teaching.TeacherScope;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CRUD for school classes. Writes are transactional (and journalled); uniqueness of name is checked up front. */
@Service
public class SchoolClassService {

    private final SchoolClassRepository repository;
    private final AuditLog auditLog;
    private final TeacherScope scope;
    private final UserRepository userRepository;

    public SchoolClassService(SchoolClassRepository repository, AuditLog auditLog, TeacherScope scope,
                              UserRepository userRepository) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.scope = scope;
        this.userRepository = userRepository;
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
        SchoolClass schoolClass = new SchoolClass(name, description);
        schoolClass.assignTeacher(scope.currentUserId()); // whoever creates the class owns it
        SchoolClass saved = repository.save(schoolClass);
        auditLog.record(AuditEvent.of(AuditAction.CLASS_CREATED, saved.getId(), saved.getName()));
        return saved;
    }

    /**
     * Hands a class (with its books, homework and invite links) to another teacher — an ADMIN's decision. The new owner
     * must be an active TEACHER or ADMIN.
     */
    @Transactional
    public SchoolClass assignTeacher(Long id, Long teacherId) {
        SchoolClass schoolClass = get(id);
        User teacher = userRepository.findById(teacherId)
                .filter(u -> !u.isErased() && u.getStatus() == AccountStatus.ACTIVE)
                .filter(u -> u.getRole() == Role.TEACHER || u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ContentNotFoundException("Teacher", teacherId));
        schoolClass.assignTeacher(teacher.getId());
        auditLog.record(AuditEvent.of(AuditAction.CLASS_UPDATED, id, schoolClass.getName())
                .about(teacher).details("clasa a fost dată profesorului " + teacher.getFullName()));
        return repository.save(schoolClass);
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
                blockers.add(RoCount.of(quizzes, "test destinat ei", "teste destinate ei"));
                fixes.add("Testele le ștergi sau le muți la „Toți elevii” din pagina Teste.");
            }
            throw new ContentInUseException("Clasa „" + schoolClass.getName() + "” nu poate fi ștearsă: are "
                    + String.join(" și ", blockers) + ". " + String.join(" ", fixes));
        }
        repository.deleteEnrollments(id);
        repository.deleteById(id);
        auditLog.record(AuditEvent.of(AuditAction.CLASS_DELETED, id, schoolClass.getName()));
    }
}
