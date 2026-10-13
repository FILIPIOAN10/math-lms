package ro.mathlms.enchapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EnChapterRepository extends JpaRepository<EnChapter, Long> {

    List<EnChapter> findAllByOrderByPositionAscIdAsc();

    Optional<EnChapter> findByCode(String code);

    /** Where a new chapter goes: after the last one. */
    @Query("select coalesce(max(c.position), 0) from EnChapter c")
    int maxPosition();
}
