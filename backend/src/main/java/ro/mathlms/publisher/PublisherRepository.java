package ro.mathlms.publisher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    /** Alphabetical, whatever the case: the list and the test's dropdown read in this order. */
    @Query("select p from Publisher p order by lower(p.name), p.id")
    List<Publisher> findAllByName();

    @Query("select count(p) > 0 from Publisher p where lower(p.name) = lower(:name) and (:exceptId is null or p.id <> :exceptId)")
    boolean nameTaken(@Param("name") String name, @Param("exceptId") Long exceptId);

    /** How many tests name each publisher: [publisherId, count]. */
    @Query("select q.publisher.id, count(q) from Quiz q where q.publisher is not null group by q.publisher.id")
    List<Object[]> quizCounts();

    /** Before a publisher goes (its books with it): its tests stay, without either (the FK does the same; this keeps loaded tests right). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Quiz q set q.publisher = null, q.book = null where q.publisher.id = :id")
    int detachQuizzes(@Param("id") Long id);
}
