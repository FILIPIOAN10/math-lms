package ro.mathlms.content;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CRUD for school classes. Writes are transactional; uniqueness of name is checked up front. */
@Service
public class SchoolClassService {

    private final SchoolClassRepository repository;

    public SchoolClassService(SchoolClassRepository repository) {
        this.repository = repository;
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
        return repository.save(new SchoolClass(name, description));
    }

    @Transactional
    public SchoolClass update(Long id, String name, String description) {
        SchoolClass schoolClass = get(id);
        if (!schoolClass.getName().equals(name) && repository.existsByName(name)) {
            throw new DuplicateContentException("A class named '" + name + "' already exists");
        }
        schoolClass.update(name, description);
        return repository.save(schoolClass);
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
    }
}
