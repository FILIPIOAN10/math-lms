package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AttemptGrantRepository extends JpaRepository<AttemptGrant, Long> {

    @Query("select g from AttemptGrant g where g.quiz.id = :quizId and g.student.id = :studentId")
    Optional<AttemptGrant> findOne(@Param("quizId") Long quizId, @Param("studentId") Long studentId);
}
