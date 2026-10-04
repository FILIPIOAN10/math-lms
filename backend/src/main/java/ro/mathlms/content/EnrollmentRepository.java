package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findBySchoolClassId(Long schoolClassId);

    List<Enrollment> findByStudentId(Long studentId);

    /**
     * A student's enrollments with the class eagerly fetched (open-in-view is off, so the DTO
     * mapping after the transaction closes must not trigger lazy loads). Ordered by class name.
     */
    @Query("select e from Enrollment e join fetch e.schoolClass"
            + " where e.student.id = :studentId order by e.schoolClass.name")
    List<Enrollment> findByStudentIdFetchClass(@Param("studentId") Long studentId);

    /** Just the ids of the classes a student attends — cheap input for visibility checks. */
    @Query("select e.schoolClass.id from Enrollment e where e.student.id = :studentId")
    List<Long> findClassIdsByStudentId(@Param("studentId") Long studentId);

    boolean existsByStudentIdAndSchoolClassId(Long studentId, Long schoolClassId);

    /** GDPR erasure: drop which classes a student attended (personal, not retained). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Enrollment e where e.student.id = :userId")
    void deleteByStudentId(@Param("userId") Long userId);

    /**
     * Roster of a class with the student eagerly fetched, so the DTO mapping can read the
     * student's name/email after the transaction closes (open-in-view is off). Ordered by name.
     */
    @Query("select e from Enrollment e join fetch e.student"
            + " where e.schoolClass.id = :classId order by e.student.fullName")
    List<Enrollment> findBySchoolClassIdFetchStudent(@Param("classId") Long classId);
}
