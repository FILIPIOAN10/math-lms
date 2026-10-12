package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuizItemPartRepository extends JpaRepository<QuizItemPart, Long> {

    List<QuizItemPart> findByItemIdOrderByPosition(Long itemId);

    /** Every part of several items at once (a whole quiz), in order. */
    List<QuizItemPart> findByItemIdInOrderByItemIdAscPositionAsc(Collection<Long> itemIds);

    @Modifying
    @Query("delete from QuizItemPart p where p.item.id = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);
}
