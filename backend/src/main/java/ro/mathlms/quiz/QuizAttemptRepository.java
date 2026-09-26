package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** Loads each attempt's quiz in the same query — the list reads its title (no N+1). */
    @EntityGraph(attributePaths = "quiz")
    List<QuizAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<QuizAttempt> findByQuizId(Long quizId);

    Optional<QuizAttempt> findByQuizIdAndStudentIdAndStatus(
            Long quizId, Long studentId, QuizAttemptStatus status);

    /** The teacher's grading queue: attempts in one status, with quiz + student fetched up front. */
    @Query("select a from QuizAttempt a join fetch a.quiz join fetch a.student "
            + "where a.status = :status order by a.submittedAt asc")
    List<QuizAttempt> findByStatusForGrading(@Param("status") QuizAttemptStatus status);
}
