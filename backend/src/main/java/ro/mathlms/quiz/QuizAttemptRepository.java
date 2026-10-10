package ro.mathlms.quiz;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** Loads each attempt's quiz in the same query — the list reads its title (no N+1). */
    @EntityGraph(attributePaths = "quiz")
    List<QuizAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    /** A student's graded attempts, oldest first, with the quiz fetched (its title feeds the progress chart). */
    @EntityGraph(attributePaths = "quiz")
    List<QuizAttempt> findByStudentIdAndStatusAndModeOrderBySubmittedAtAsc(
            Long studentId, QuizAttemptStatus status, AttemptMode mode);

    List<QuizAttempt> findByQuizId(Long quizId);

    /** Final scores of a quiz's GRADED attempts — the input of the teacher's average and distribution. */
    @Query("select a.score from QuizAttempt a where a.quiz.id = :quizId and a.status = ro.mathlms.quiz.QuizAttemptStatus.GRADED"
            + " and a.mode = ro.mathlms.quiz.AttemptMode.TEST") // a practice has no score and is never part of the statistics
    List<Integer> findGradedScoresByQuizId(@Param("quizId") Long quizId);

    Optional<QuizAttempt> findByQuizIdAndStudentIdAndStatusAndMode(
            Long quizId, Long studentId, QuizAttemptStatus status, AttemptMode mode);

    /**
     * Loads the attempt with a row lock (SELECT ... FOR UPDATE) held until the transaction ends, so
     * answering, uploading and submitting the same attempt run one after another, never interleaved.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuizAttempt a where a.id = :id")
    Optional<QuizAttempt> findByIdForUpdate(@Param("id") Long id);

    /**
     * In-progress attempts whose deadline is before {@code cutoff} (the expiry job passes now minus the grace
     * period). Ids only: each one is then handed in under its own lock and transaction.
     */
    @Query("select a.id from QuizAttempt a where a.status = ro.mathlms.quiz.QuizAttemptStatus.IN_PROGRESS "
            + "and a.deadlineAt is not null and a.deadlineAt < :cutoff order by a.deadlineAt")
    List<Long> findOverdueIds(@Param("cutoff") Instant cutoff);

    /**
     * The graded-TEST attempts of the given students at one quiz, started at or after {@code since}, with the student
     * loaded - the input of the homework progress (a practice or an attempt from before the assignment never counts).
     */
    @Query("select a from QuizAttempt a join fetch a.student where a.quiz.id = :quizId and a.student.id in :studentIds"
            + " and a.mode = ro.mathlms.quiz.AttemptMode.TEST and a.startedAt >= :since")
    List<QuizAttempt> findForAssignment(@Param("quizId") Long quizId,
                                        @Param("studentIds") java.util.Collection<Long> studentIds,
                                        @Param("since") Instant since);

    /** One student's attempts at several quizzes, quiz loaded - what their homework list is built from. */
    @EntityGraph(attributePaths = "quiz")
    List<QuizAttempt> findByStudentIdAndQuizIdIn(Long studentId, java.util.Collection<Long> quizIds);

    /** The teacher's grading queue: attempts in one status, with quiz + student fetched up front. */
    @Query("select a from QuizAttempt a join fetch a.quiz join fetch a.student "
            + "where a.status = :status and a.mode = ro.mathlms.quiz.AttemptMode.TEST order by a.submittedAt asc")
    List<QuizAttempt> findByStatusForGrading(@Param("status") QuizAttemptStatus status);

    /** GDPR erasure: the teacher's comment on a paper is free text about the student - it goes, the grade stays. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update QuizAttempt a set a.teacherComment = null where a.student.id = :userId")
    void clearTeacherCommentsByStudentId(@Param("userId") Long userId);
}
