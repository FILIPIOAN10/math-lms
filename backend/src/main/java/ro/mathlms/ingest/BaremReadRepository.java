package ro.mathlms.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BaremReadRepository extends JpaRepository<BaremRead, Long> {

    /** Claims the oldest waiting reading; {@code SKIP LOCKED} lets two app instances work side by side (as ai_imports). */
    @Query(value = """
            SELECT * FROM ai_barem_reads
            WHERE status = 'QUEUED'
            ORDER BY id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<BaremRead> claimNext();

    Optional<BaremRead> findByIdAndQuizIdAndTeacherId(Long id, Long quizId, Long teacherId);

    /** Barem photos one teacher sent since {@code since} (the daily limit). */
    @Query("select coalesce(sum(b.pageCount), 0) from BaremRead b where b.teacherId = :teacherId and b.createdAt >= :since")
    long pagesSince(@Param("teacherId") Long teacherId, @Param("since") Instant since);

    /** Worked on for too long: the app restarted (or hung) mid-read. */
    List<BaremRead> findByStatusAndStartedAtBefore(AiImportStatus status, Instant before);
}
