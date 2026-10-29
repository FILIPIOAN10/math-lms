package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    Optional<SchoolClass> findByName(String name);

    boolean existsByName(String name);

    /** Whether this account still owns a class (it must hand it over before erasing itself). */
    boolean existsByTeacherId(Long teacherId);

    @Query("select count(b) from Book b where b.schoolClass.id = :classId")
    long countBooks(@Param("classId") Long classId);

    /** Quizzes reserved for this class (a quiz "for everyone" has no class and does not count). */
    @Query("select count(q) from Quiz q where q.schoolClass.id = :classId")
    long countQuizzes(@Param("classId") Long classId);

    /** Bulk, so it runs before the class row is deleted in the same transaction. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Enrollment e where e.schoolClass.id = :classId")
    void deleteEnrollments(@Param("classId") Long classId);

    /**
     * One row per class for the teacher's class list: how many students are enrolled, how many tests are reserved for
     * it, and the mean 1–10 grade of its graded tests (practice has no grade; a test worth no points is skipped). The
     * grade is the Romanian one the app shows everywhere: 1 + 9 × the share of points.
     */
    @Query(nativeQuery = true, value = """
            select c.id as classId,
                   (select count(*) from enrollments e where e.school_class_id = c.id) as students,
                   (select count(*) from quizzes q where q.school_class_id = c.id) as quizzes,
                   (select avg(1 + 9.0 * a.score / m.total)
                      from quiz_attempts a
                      join quizzes q on q.id = a.quiz_id
                      join (select quiz_id, sum(points) as total from quiz_items group by quiz_id) m on m.quiz_id = q.id
                     where q.school_class_id = c.id and a.status = 'GRADED' and a.mode = 'TEST'
                       and a.score is not null and m.total > 0) as average
              from school_classes c
            """)
    List<ClassSummaryRow> summaries();

    interface ClassSummaryRow {
        Long getClassId();

        Long getStudents();

        Long getQuizzes();

        /** Null while the class has no graded test. */
        Double getAverage();
    }
}
