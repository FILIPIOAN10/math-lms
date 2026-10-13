package ro.mathlms.enchapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EnChapterSlotRepository extends JpaRepository<EnChapterSlot, String> {

    /** Every default link with its chapter: a couple of dozen rows, read whole. */
    @Query("select s from EnChapterSlot s join fetch s.chapter")
    List<EnChapterSlot> findAllFetched();

    /** The places on the paper that point at this chapter by default ("I.1", "III.5.b"). */
    @Query("select s.slot from EnChapterSlot s where s.chapter.id = :chapterId")
    List<String> findSlotsOfChapter(@org.springframework.data.repository.query.Param("chapterId") Long chapterId);

    /** Every link, deleted at once and forgotten by the persistence context, so new rows with the same keys can follow. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EnChapterSlot s")
    void deleteEverything();
}
