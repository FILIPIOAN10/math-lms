package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AttemptGrantRepository extends JpaRepository<AttemptGrant, Long> {

    /**
     * "+1 încercare" in one statement: the first grant (1) or one more, never past {@code max}. Postgres serializes two
     * at once on the (quiz, student) row, so both count - the read-then-save it replaced lost one, or hit the unique key.
     * 1 = granted, 0 = already at {@code max}.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into attempt_grants (quiz_id, student_id, extra, created_at) values (:quizId, :studentId, 1, :now)
            on conflict (quiz_id, student_id) do update set extra = attempt_grants.extra + 1
            where attempt_grants.extra < :max""")
    int addOne(@Param("quizId") Long quizId, @Param("studentId") Long studentId, @Param("now") Instant now,
               @Param("max") int max);

    /** The extra attempts one student has at one test, if any. */
    @Query("select g.extra from AttemptGrant g where g.quiz.id = :quizId and g.student.id = :studentId")
    Optional<Integer> findExtra(@Param("quizId") Long quizId, @Param("studentId") Long studentId);
}
