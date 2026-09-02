package ro.mathlms.content;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ExerciseRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private EntityManager entityManager;

    private Chapter persistChapter() {
        SchoolClass klass = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        Book book = bookRepository.save(new Book(klass, "M1", null));
        return chapterRepository.save(new Chapter(book, "Ecuații", null));
    }

    @Test
    void findsExercisesOfAChapterInInsertionOrder() {
        Chapter chapter = persistChapter();
        Exercise first = exerciseRepository.save(new Exercise(chapter, "primul", null, null));
        Exercise second = exerciseRepository.save(new Exercise(chapter, "al doilea", null, null));

        List<Exercise> exercises = exerciseRepository.findByChapterIdOrderById(chapter.getId());

        assertThat(exercises).extracting(Exercise::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void versionStartsAtZeroAndIncrementsOnUpdate() {
        Chapter chapter = persistChapter();
        Exercise ex = exerciseRepository.saveAndFlush(new Exercise(chapter, "enunț", null, null));
        assertThat(ex.getVersion()).isZero();

        ex.update("enunț modificat", null, Difficulty.EASY);
        exerciseRepository.saveAndFlush(ex);

        assertThat(ex.getVersion()).isEqualTo(1L);
    }

    @Test
    void concurrentEditConflictsViaOptimisticLock() {
        Chapter chapter = persistChapter();
        Exercise ex = exerciseRepository.saveAndFlush(new Exercise(chapter, "enunț", null, null));

        // Simulate another transaction that already bumped the row's version.
        entityManager.createNativeQuery(
                        "UPDATE exercises SET version = version + 1 WHERE id = :id")
                .setParameter("id", ex.getId())
                .executeUpdate();

        // Our in-memory copy still thinks version=0, so flushing its update must conflict.
        ex.update("enunț modificat", null, null);
        assertThatThrownBy(() -> exerciseRepository.saveAndFlush(ex))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
