package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The summary DTO shows the class name and open-in-view is off, so every query whose result is
 * mapped to a DTO fetches {@code schoolClass} up front instead of lazy-loading it later.
 */
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** Whether this account still owns a test (it must hand it over before erasing itself). */
    boolean existsByTeacherId(Long teacherId);

    @Override
    @EntityGraph(attributePaths = {"schoolClass", "publisher", "book"})
    List<Quiz> findAll();

    @Override
    @EntityGraph(attributePaths = {"schoolClass", "publisher", "book"})
    Optional<Quiz> findById(Long id);

    List<Quiz> findByStatusOrderByTitle(QuizStatus status);

    /** Attempts of any mode (test or practice): each one holds a student's answers. */
    @Query("select count(a) from QuizAttempt a where a.quiz.id = :quizId")
    long countAttempts(@Param("quizId") Long quizId);

    /** Attempts handed in (a test waiting for or past grading, or a finished practice): answers that count. */
    @Query("select count(a) from QuizAttempt a where a.quiz.id = :quizId"
            + " and a.status <> ro.mathlms.quiz.QuizAttemptStatus.IN_PROGRESS")
    long countHandedInAttempts(@Param("quizId") Long quizId);

    /** Students writing this quiz now, as a test or a practice: once it is unpublished, whoever leaves cannot come back. */
    @Query("select count(distinct a.student.id) from QuizAttempt a where a.quiz.id = :quizId"
            + " and a.status = ro.mathlms.quiz.QuizAttemptStatus.IN_PROGRESS")
    long countStudentsWriting(@Param("quizId") Long quizId);

    /** How many classes have this quiz as homework. */
    @Query("select count(a) from Assignment a where a.quiz.id = :quizId")
    long countHomeworkClasses(@Param("quizId") Long quizId);

    /** When each quiz was last given as homework to one of these classes (not an empty list, see below). */
    @Query("select new ro.mathlms.quiz.HomeworkGiven(a.quiz.id, max(a.createdAt)) from Assignment a"
            + " where a.schoolClass.id in :classIds group by a.quiz.id")
    List<HomeworkGiven> findHomeworkGiven(@Param("classIds") Collection<Long> classIds);

    /** Classes this quiz is homework for, other than {@code classId}: moving the quiz there would strand them. */
    @Query("select a.schoolClass.name from Assignment a where a.quiz.id = :quizId and a.schoolClass.id <> :classId"
            + " order by a.schoolClass.name")
    List<String> findAssignedClassNamesOtherThan(@Param("quizId") Long quizId, @Param("classId") Long classId);

    /** Published quizzes open to every student (not assigned to any class). */
    @EntityGraph(attributePaths = {"schoolClass", "publisher", "book"})
    List<Quiz> findByStatusAndSchoolClassIsNullOrderByTitle(QuizStatus status);

    /**
     * Published quizzes a student of {@code classIds} may take: those for everyone plus those
     * assigned to one of these classes. {@code classIds} must not be empty (an empty IN list is
     * not portable) — callers use the "for everyone" query for a student without classes.
     */
    @EntityGraph(attributePaths = {"schoolClass", "publisher", "book"})
    @Query("select q from Quiz q where q.status = :status"
            + " and (q.schoolClass is null or q.schoolClass.id in :classIds) order by q.title")
    List<Quiz> findVisibleToClasses(@Param("status") QuizStatus status,
                                    @Param("classIds") Collection<Long> classIds);

    /**
     * One row per quiz for the teacher's list: its questions, the papers handed in, the mean 1–10 grade of its students -
     * each counted once, with their highest grade - (practice has no grade; a test worth no points is skipped) and the
     * homework deadline - the next one still to come, else the last one that passed.
     */
    @Query(nativeQuery = true, value = """
            select q.id as quizId,
                   (select count(*) from quiz_items i where i.quiz_id = q.id) as questions,
                   (select count(*) from quiz_attempts a
                     where a.quiz_id = q.id and a.mode = 'TEST' and a.status <> 'IN_PROGRESS') as papers,
                   (select avg(1 + 9.0 * best.score
                               / nullif((select sum(i.points) from quiz_items i where i.quiz_id = q.id), 0))
                      from (select max(a.score) as score
                              from quiz_attempts a
                             where a.quiz_id = q.id and a.mode = 'TEST' and a.status = 'GRADED' and a.score is not null
                             group by a.student_id) best) as average,
                   (select coalesce(min(h.due_at) filter (where h.due_at >= now()), max(h.due_at))
                      from assignments h where h.quiz_id = q.id) as dueAt
              from quizzes q
            """)
    List<QuizListRow> listSummaries();

    interface QuizListRow {
        Long getQuizId();

        Long getQuestions();

        Long getPapers();

        /** Null until a paper is graded. */
        Double getAverage();

        /** Null when the quiz is not homework. */
        Instant getDueAt();
    }
}
