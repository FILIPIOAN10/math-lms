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

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    boolean existsByQuizIdAndSchoolClassId(Long quizId, Long schoolClassId);

    /** Every assignment with its quiz and class loaded (open-in-view is off), the next deadline first. */
    @Query("select a from Assignment a join fetch a.quiz join fetch a.schoolClass order by a.dueAt desc")
    List<Assignment> findAllFetched();

    @Query("select a from Assignment a join fetch a.quiz join fetch a.schoolClass where a.id = :id")
    Optional<Assignment> findByIdFetched(@Param("id") Long id);

    /** The assignments of the given classes, quiz and class loaded - what a student's homework list is built from. */
    @Query("select a from Assignment a join fetch a.quiz join fetch a.schoolClass where a.schoolClass.id in :classIds")
    List<Assignment> findBySchoolClassIdsFetched(@Param("classIds") Collection<Long> classIds);

    /** Row lock, so two reminder runs (or two app instances) can never queue the same assignment's reminders twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") Long id);

    /** Assignments still waiting for their reminder whose deadline falls in {@code (now, until]}. */
    @Query("select a.id from Assignment a where a.reminderSentAt is null and a.dueAt > :now and a.dueAt <= :until"
            + " order by a.dueAt")
    List<Long> findIdsDueForReminder(@Param("now") Instant now, @Param("until") Instant until);
}
