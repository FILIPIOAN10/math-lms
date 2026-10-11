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

    @Query("select count(e) from Exercise e where e.chapter.id = :chapterId")
    long countExercises(@Param("chapterId") Long chapterId);

    /** The chapters of these classes with their book and class, for lists and the mastery map (E6). */
    @Query("select c from Chapter c join fetch c.book b join fetch b.schoolClass s where s.id in :classIds")
    List<Chapter> findByClassIdsFetched(@Param("classIds") java.util.Collection<Long> classIds);

    /** These chapters with their book and class. */
    @Query("select c from Chapter c join fetch c.book b join fetch b.schoolClass where c.id in :ids")
    List<Chapter> findByIdsFetched(@Param("ids") java.util.Collection<Long> ids);

    /** Every chapter with its book and class - the teacher's choice list when tagging a quiz. */
    @Query("select c from Chapter c join fetch c.book b join fetch b.schoolClass")
    List<Chapter> findAllFetched();
}
