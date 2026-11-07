package ro.mathlms.publisher;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublisherBookRepository extends JpaRepository<PublisherBook, Long> {

    /** Every book of every publisher, alphabetical within each: one query for the whole list. */
    @Query("select b from PublisherBook b order by lower(b.title), b.id")
    List<PublisherBook> findAllByTitle();

    @Override
    @EntityGraph(attributePaths = "publisher")
    Optional<PublisherBook> findById(Long id);

    @Query("select count(b) > 0 from PublisherBook b where b.publisher.id = :publisherId and lower(b.title) = lower(:title)"
            + " and (:exceptId is null or b.id <> :exceptId)")
    boolean titleTaken(@Param("publisherId") Long publisherId, @Param("title") String title,
                       @Param("exceptId") Long exceptId);

    /** How many tests each book holds: [bookId, count]. */
    @Query("select q.book.id, count(q) from Quiz q where q.book is not null group by q.book.id")
    List<Object[]> quizCounts();

    /** Before a book goes: its tests stay, without it (the FK does the same; this keeps loaded tests right). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Quiz q set q.book = null where q.book.id = :id")
    int detachQuizzes(@Param("id") Long id);
}
