package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    /** The id of the class a book belongs to — a scalar, so it works with open-in-view off. */
    @Query("select b.schoolClass.id from Book b where b.id = :id")
    Optional<Long> findClassIdById(@Param("id") Long id);

    List<Book> findBySchoolClassIdOrderByTitle(Long schoolClassId);

    boolean existsBySchoolClassIdAndTitle(Long schoolClassId, String title);
}
