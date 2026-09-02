package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {
    List<QuizOption> findByItemIdOrderByPosition(Long itemId);

    void deleteByItemId(Long itemId);
}
