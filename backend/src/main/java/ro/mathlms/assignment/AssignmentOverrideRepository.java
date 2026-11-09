package ro.mathlms.assignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssignmentOverrideRepository extends JpaRepository<AssignmentOverride, Long> {

    /** Every exception of one homework - the teacher's status list and the reminders read them in one query. */
    @Query("select o from AssignmentOverride o join fetch o.student where o.assignment.id = :assignmentId")
    List<AssignmentOverride> findByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** The exceptions of several homeworks - the teacher's homework list, one query for all of it. */
    @Query("select o from AssignmentOverride o join fetch o.student where o.assignment.id in :assignmentIds")
    List<AssignmentOverride> findByAssignmentIdIn(@Param("assignmentIds") Collection<Long> assignmentIds);

    /** One student's exceptions - what their homework list is built from. */
    @Query("select o from AssignmentOverride o join fetch o.assignment where o.student.id = :studentId")
    List<AssignmentOverride> findByStudentId(@Param("studentId") Long studentId);

    @Query("select o from AssignmentOverride o where o.assignment.id = :assignmentId and o.student.id = :studentId")
    Optional<AssignmentOverride> findOne(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);
}
