package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuizItemRepository extends JpaRepository<QuizItem, Long> {

    long countByQuizId(Long quizId);

    List<QuizItem> findByQuizIdOrderByPosition(Long quizId);

    /**
     * Maximum score per quiz for a set of quizzes in ONE query (no per-quiz lookups). A quiz without
     * items is simply absent from the result.
     */
    @Query("select new ro.mathlms.quiz.QuizMaxScore(i.quiz.id, sum(i.points))"
            + " from QuizItem i where i.quiz.id in :quizIds group by i.quiz.id")
    List<QuizMaxScore> sumPointsByQuiz(@Param("quizIds") Collection<Long> quizIds);
}
