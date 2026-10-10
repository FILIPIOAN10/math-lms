package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    Optional<SchoolClass> findByName(String name);

    boolean existsByName(String name);

    @Query("select count(b) from Book b where b.schoolClass.id = :classId")
    long countBooks(@Param("classId") Long classId);

    /** Quizzes reserved for this class (a quiz "for everyone" has no class and does not count). */
    @Query("select count(q) from Quiz q where q.schoolClass.id = :classId")
    long countQuizzes(@Param("classId") Long classId);

    /** Bulk, so it runs before the class row is deleted in the same transaction. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Enrollment e where e.schoolClass.id = :classId")
    void deleteEnrollments(@Param("classId") Long classId);
}
