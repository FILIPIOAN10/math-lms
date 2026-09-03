package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<QuizAttempt> findByQuizId(Long quizId);

    Optional<QuizAttempt> findByQuizIdAndStudentIdAndStatus(
            Long quizId, Long studentId, QuizAttemptStatus status);
}
