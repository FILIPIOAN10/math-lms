package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {
    List<QuizOption> findByItemIdOrderByPosition(Long itemId);

    /** The options of several questions at once, in order, in one query. */
    List<QuizOption> findByItemIdInOrderByItemIdAscPositionAsc(Collection<Long> itemIds);

    void deleteByItemId(Long itemId);
}
