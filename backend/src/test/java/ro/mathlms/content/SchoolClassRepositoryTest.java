package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchoolClassRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SchoolClassRepository repository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void savesAndFindsByName() {
        repository.save(new SchoolClass("Clasa a 9-a", "Algebră"));

        Optional<SchoolClass> found = repository.findByName("Clasa a 9-a");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getDescription()).isEqualTo("Algebră");
    }

    @Test
    void existsByNameReflectsPersistedRows() {
        repository.save(new SchoolClass("Clasa a 10-a", null));

        assertThat(repository.existsByName("Clasa a 10-a")).isTrue();
        assertThat(repository.existsByName("Clasa a 11-a")).isFalse();
    }

    @Test
    void enforcesUniqueName() {
        repository.saveAndFlush(new SchoolClass("Clasa a 9-a", "prima"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new SchoolClass("Clasa a 9-a", "a doua")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countsWhatStillHangsOffAClassBookAndChapter() {
        SchoolClass ninth = repository.save(new SchoolClass("Clasa a 9-a", null));
        Book algebra = em.persist(new Book(ninth, "Algebra", null));
        em.persist(new Book(ninth, "Geometrie", null));
        Chapter equations = em.persist(new Chapter(algebra, "Ecuații", null));
        em.persist(new Exercise(equations, "$x+1=2$", null, null));
        Quiz quiz = new Quiz("Teza", null);
        quiz.assignToClass(ninth);
        em.persist(quiz);
        em.flush();

        assertThat(repository.countBooks(ninth.getId())).isEqualTo(2);
        assertThat(repository.countQuizzes(ninth.getId())).isEqualTo(1);
        assertThat(bookRepository.countChapters(algebra.getId())).isEqualTo(1);
        assertThat(chapterRepository.countExercises(equations.getId())).isEqualTo(1);
    }

    @Test
    void aClassWithEnrolledStudentsCanBeDeletedOnceTheyAreUnenrolled() {
        SchoolClass ninth = repository.save(new SchoolClass("Clasa a 9-a", null));
        User ana = em.persist(new User("ana@scoala.ro", "Ana", Role.STUDENT));
        em.persist(new Enrollment(ana, ninth));
        em.flush();

        repository.deleteEnrollments(ninth.getId());
        repository.deleteById(ninth.getId());
        em.flush();

        assertThat(repository.existsById(ninth.getId())).isFalse();
        assertThat(em.find(User.class, ana.getId())).isNotNull();
    }
}
