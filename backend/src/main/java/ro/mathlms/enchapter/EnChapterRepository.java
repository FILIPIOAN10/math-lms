package ro.mathlms.enchapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EnChapterRepository extends JpaRepository<EnChapter, Long> {

    List<EnChapter> findAllByOrderByPositionAscIdAsc();

    Optional<EnChapter> findByCode(String code);

    /**
     * The questions and requirements of every test that the teacher pointed at this chapter themselves. Native: the
     * quiz tables belong to the quiz package, which already depends on this one.
     */
    @Query(value = "select (select count(*) from quiz_items where en_chapter_id = :id)"
            + " + (select count(*) from quiz_item_parts where en_chapter_id = :id)", nativeQuery = true)
    long countChosenBy(@org.springframework.data.repository.query.Param("id") Long id);

    /** Where a new chapter goes: after the last one. */
    @Query("select coalesce(max(c.position), 0) from EnChapter c")
    int maxPosition();
}
