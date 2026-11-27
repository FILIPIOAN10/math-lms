package ro.mathlms.recap;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RecapExerciseRepository extends JpaRepository<RecapExercise, Long> {

    Optional<RecapExercise> findBySourceKey(String sourceKey);

    @Query("select e.sourceKey from RecapExercise e")
    Set<String> findAllKeys();

    /** The whole collection in book order (a few hundred rows: the list screen filters it in memory). */
    @EntityGraph(attributePaths = "enChapter")
    List<RecapExercise> findAllByOrderByTemaAscNumberAscIdAsc();

    /** The approved exercises of one EN chapter, in book order. */
    @EntityGraph(attributePaths = "enChapter")
    List<RecapExercise> findByEnChapterIdAndReviewOrderByTemaAscNumberAscIdAsc(Long enChapterId, RecapReview review);

    /** The tests of a class, for "not used with this class yet". Native: the quiz package is not this one's to read. */
    @Query(value = "select id from quizzes where school_class_id = :classId", nativeQuery = true)
    Set<Long> findQuizIdsOfClass(@Param("classId") Long classId);
}
