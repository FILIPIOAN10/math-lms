package ro.mathlms.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims up to {@code limit} due events. {@code FOR UPDATE SKIP LOCKED} lets several dispatcher
     * instances (or ticks) drain the table in parallel without ever handing the same row to two of
     * them; the locks last until the calling transaction ends, by which point every claimed row has
     * been marked DONE or rescheduled.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("limit") int limit);

    long countByStatus(OutboxStatus status);

    List<OutboxEvent> findByEventTypeOrderById(String eventType);

    /** Housekeeping: finished rows are only history. DEAD rows are kept — they need a human. */
    @Modifying
    @Query("delete from OutboxEvent e where e.status = ro.mathlms.outbox.OutboxStatus.DONE and e.updatedAt < :before")
    int deleteDoneBefore(@Param("before") Instant before);
}
