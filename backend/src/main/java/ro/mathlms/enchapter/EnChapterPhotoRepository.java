package ro.mathlms.enchapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnChapterPhotoRepository extends JpaRepository<EnChapterPhoto, Long> {

    List<EnChapterPhoto> findByChapterIdOrderByPositionAscIdAsc(Long chapterId);

    Optional<EnChapterPhoto> findByIdAndChapterId(Long id, Long chapterId);

    long countByChapterId(Long chapterId);

    @Query("select coalesce(max(p.position), 0) from EnChapterPhoto p where p.chapter.id = :chapterId")
    int maxPosition(@Param("chapterId") Long chapterId);

    /** chapter id → number of photos, for the list (one query for every chapter). */
    @Query("select p.chapter.id, count(p) from EnChapterPhoto p group by p.chapter.id")
    List<Object[]> countPerChapter();
}
