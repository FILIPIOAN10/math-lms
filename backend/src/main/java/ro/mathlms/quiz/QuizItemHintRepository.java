package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizItemHintRepository extends JpaRepository<QuizItemHint, Long> {

    List<QuizItemHint> findByItemIdOrderByPosition(Long itemId);

    /**
     * Deletes with a bulk statement that runs NOW. A derived {@code deleteBy...} would queue the deletes behind the
     * inserts of the same transaction, and replacing an item's hints would then hit the unique (item, position) key
     * before the old rows were gone.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from QuizItemHint h where h.item.id = :itemId")
    void deleteByItemId(@Param("itemId") Long itemId);

    /** Hint counts for every item of a quiz that has any, in a single query. */
    @Query("select new ro.mathlms.quiz.HintCount(h.item.id, count(h)) from QuizItemHint h"
            + " where h.item.quiz.id = :quizId group by h.item.id")
    List<HintCount> countsByQuizId(@Param("quizId") Long quizId);
}
