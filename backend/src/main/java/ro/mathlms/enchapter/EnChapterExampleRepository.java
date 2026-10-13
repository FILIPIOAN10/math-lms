package ro.mathlms.enchapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnChapterExampleRepository extends JpaRepository<EnChapterExample, Long> {

    List<EnChapterExample> findByChapterIdOrderByPositionAscIdAsc(Long chapterId);

    Optional<EnChapterExample> findByIdAndChapterId(Long id, Long chapterId);

    long countByChapterId(Long chapterId);

    @Query("select coalesce(max(e.position), 0) from EnChapterExample e where e.chapter.id = :chapterId")
    int maxPosition(@Param("chapterId") Long chapterId);

    /** chapter id → number of worked examples, for the list. */
    @Query("select e.chapter.id, count(e) from EnChapterExample e group by e.chapter.id")
    List<Object[]> countPerChapter();
}
