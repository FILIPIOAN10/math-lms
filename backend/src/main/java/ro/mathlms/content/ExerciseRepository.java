package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    /** The id of the class an exercise belongs to (via its chapter and book). */
    @Query("select e.chapter.book.schoolClass.id from Exercise e where e.id = :id")
    Optional<Long> findClassIdById(@Param("id") Long id);

    List<Exercise> findByChapterIdOrderById(Long chapterId);
}
