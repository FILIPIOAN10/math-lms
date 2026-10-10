package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The summary DTO shows the class name and open-in-view is off, so every query whose result is
 * mapped to a DTO fetches {@code schoolClass} up front instead of lazy-loading it later.
 */
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @Override
    @EntityGraph(attributePaths = "schoolClass")
    List<Quiz> findAll();

    @Override
    @EntityGraph(attributePaths = "schoolClass")
    Optional<Quiz> findById(Long id);

    List<Quiz> findByStatusOrderByTitle(QuizStatus status);

    /** Attempts of any mode (test or practice): each one holds a student's answers. */
    @Query("select count(a) from QuizAttempt a where a.quiz.id = :quizId")
    long countAttempts(@Param("quizId") Long quizId);

    /** Published quizzes open to every student (not assigned to any class). */
    @EntityGraph(attributePaths = "schoolClass")
    List<Quiz> findByStatusAndSchoolClassIsNullOrderByTitle(QuizStatus status);

    /**
     * Published quizzes a student of {@code classIds} may take: those for everyone plus those
     * assigned to one of these classes. {@code classIds} must not be empty (an empty IN list is
     * not portable) — callers use the "for everyone" query for a student without classes.
     */
    @EntityGraph(attributePaths = "schoolClass")
    @Query("select q from Quiz q where q.status = :status"
            + " and (q.schoolClass is null or q.schoolClass.id in :classIds) order by q.title")
    List<Quiz> findVisibleToClasses(@Param("status") QuizStatus status,
                                    @Param("classIds") Collection<Long> classIds);
}
