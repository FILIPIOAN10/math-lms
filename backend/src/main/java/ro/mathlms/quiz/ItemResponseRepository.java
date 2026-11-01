package ro.mathlms.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemResponseRepository extends JpaRepository<ItemResponse, Long> {

    List<ItemResponse> findByAttemptId(Long attemptId);

    Optional<ItemResponse> findByAttemptIdAndItemId(Long attemptId, Long itemId);

    /** Per-item aggregates over the attempts that count, in one query (see {@link ItemStat}); not an empty list. */
    @Query("select new ro.mathlms.quiz.ItemStat(r.item.id, count(r),"
            + " sum(case when r.correct = true then 1L else 0L end), sum(r.awardedPoints))"
            + " from ItemResponse r where r.attempt.id in :attemptIds group by r.item.id")
    List<ItemStat> findItemStatsByAttemptIds(@Param("attemptIds") java.util.Collection<Long> attemptIds);

    /** Every grilă choice of the attempts that count, in one query: the per-option counts and the suspect-answer check. */
    @Query("select new ro.mathlms.quiz.OptionChoice(r.attempt.id, r.item.id, r.selectedOption.id, r.correct)"
            + " from ItemResponse r where r.attempt.id in :attemptIds and r.selectedOption is not null")
    List<OptionChoice> findChoicesByAttemptIds(@Param("attemptIds") java.util.Collection<Long> attemptIds);

    /** Every answer to one question, with its attempt: a corrected answer key marks them all again. */
    @Query("select r from ItemResponse r join fetch r.attempt left join fetch r.selectedOption where r.item.id = :itemId")
    List<ItemResponse> findByItemIdFetchAttempt(@Param("itemId") Long itemId);

    /** GDPR erasure: the teacher's comments are free text about the student - they go, the points stay. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ItemResponse r set r.teacherComment = null where r.attempt.student.id = :userId")
    void clearTeacherCommentsByStudentId(@Param("userId") Long userId);

    /** All answers of these attempts, in one query (the mastery map, E6). */
    List<ItemResponse> findByAttemptIdIn(java.util.Collection<Long> attemptIds);
}
