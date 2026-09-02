package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizItemRepository extends JpaRepository<QuizItem, Long> {
    List<QuizItem> findByQuizIdOrderByPosition(Long quizId);
}
