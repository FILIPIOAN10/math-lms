package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    /** The id of the class a chapter belongs to (via its book). */
    @Query("select c.book.schoolClass.id from Chapter c where c.id = :id")
    Optional<Long> findClassIdById(@Param("id") Long id);

    List<Chapter> findByBookIdOrderByTitle(Long bookId);

    boolean existsByBookIdAndTitle(Long bookId, String title);
}
