package ro.mathlms.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiImportRepository extends JpaRepository<AiImport, Long> {

    /**
     * Claims the oldest waiting import. {@code FOR UPDATE SKIP LOCKED} (like the outbox) lets two workers - two app
     * instances, or a tick that overlaps a slow one - never take the same row; the lock lasts until the caller's short
     * claim transaction marks it RUNNING.
     */
    @Query(value = """
            SELECT * FROM ai_imports
            WHERE status = 'QUEUED'
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<AiImport> claimNext();

    Optional<AiImport> findByIdAndTeacherId(Long id, Long teacherId);

    List<AiImport> findTop20ByTeacherIdOrderByIdDesc(Long teacherId);

    /** Pages one teacher uploaded since {@code since} (the daily limit). Retries are not new pages. */
    @Query("select coalesce(sum(a.pageCount), 0) from AiImport a where a.teacherId = :teacherId and a.createdAt >= :since")
    long pagesSince(@Param("teacherId") Long teacherId, @Param("since") Instant since);

    /** Worked on for too long: the app restarted (or hung) mid-read. */
    List<AiImport> findByStatusAndStartedAtBefore(AiImportStatus status, Instant before);

    /** Imports still holding page files older than {@code before} - not while being read. */
    @Query("""
            select a from AiImport a
            where a.pageKeys <> '[]' and a.createdAt < :before and a.status <> ro.mathlms.ingest.AiImportStatus.RUNNING
            """)
    List<AiImport> withPagesCreatedBefore(@Param("before") Instant before);
}
