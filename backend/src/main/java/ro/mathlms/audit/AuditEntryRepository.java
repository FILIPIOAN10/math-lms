package ro.mathlms.audit;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /**
     * One page of the journal, newest first, below {@code before} (keyset paging: stable while rows are being added).
     * Names are joined in at read time - an erased account shows its pseudonym, a deleted one shows nothing.
     * Every filter is always bound (no null parameters): the caller widens an unused one to "everything".
     */
    @Query("""
            select new ro.mathlms.audit.AuditRow(e.id, e.occurredAt, e.action, actor.fullName, e.entityId, e.label,
                   e.details, e.studentId, student.fullName, related.fullName)
            from AuditEntry e
            left join ro.mathlms.user.User actor on actor.id = e.actorId
            left join ro.mathlms.user.User student on student.id = e.studentId
            left join ro.mathlms.user.User related on related.id = e.relatedUserId
            where e.id < :before
              and e.action in :actions
              and (:anyStudent = true or e.studentId = :studentId)
              and e.occurredAt >= :from and e.occurredAt < :to
            order by e.id desc
            """)
    List<AuditRow> findPage(@Param("before") long before, @Param("actions") Collection<AuditAction> actions,
                            @Param("anyStudent") boolean anyStudent, @Param("studentId") long studentId,
                            @Param("from") Instant from, @Param("to") Instant to, Limit limit);
}
