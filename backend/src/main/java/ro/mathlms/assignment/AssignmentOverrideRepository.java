package ro.mathlms.assignment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Students' own deadlines in {@code (now, until]} not reminded yet, excused students left out, of a published quiz -
     * what the reminder job queues (V63's partial index).
     */
    @Query("select o.id from AssignmentOverride o where o.reminderSentAt is null and o.excused = false"
            + " and o.dueAt > :now and o.dueAt <= :until"
            + " and o.assignment.quiz.status = ro.mathlms.quiz.QuizStatus.PUBLISHED order by o.dueAt")
    List<Long> findIdsDueForReminder(@Param("now") Instant now, @Param("until") Instant until);

    /** Row lock, so two reminder runs (or two app instances) never queue the same own-deadline reminder twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AssignmentOverride o join fetch o.student where o.id = :id")
    Optional<AssignmentOverride> findByIdForUpdate(@Param("id") Long id);
}
