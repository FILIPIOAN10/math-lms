package ro.mathlms.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findBySchoolClassId(Long schoolClassId);

    List<Enrollment> findByStudentId(Long studentId);

    boolean existsByStudentIdAndSchoolClassId(Long studentId, Long schoolClassId);

    /**
     * Roster of a class with the student eagerly fetched, so the DTO mapping can read the
     * student's name/email after the transaction closes (open-in-view is off). Ordered by name.
     */
    @Query("select e from Enrollment e join fetch e.student"
            + " where e.schoolClass.id = :classId order by e.student.fullName")
    List<Enrollment> findBySchoolClassIdFetchStudent(@Param("classId") Long classId);
}
