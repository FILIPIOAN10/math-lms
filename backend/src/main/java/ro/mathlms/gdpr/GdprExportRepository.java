package ro.mathlms.gdpr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GdprExportRepository extends JpaRepository<GdprExport, Long> {

    /** The account's live export (PENDING or READY), if any: at most one (a partial unique index). */
    @Query("select e from GdprExport e where e.userId = :userId and e.status in ("
            + "ro.mathlms.gdpr.GdprExport.Status.PENDING, ro.mathlms.gdpr.GdprExport.Status.READY)")
    Optional<GdprExport> findLive(@Param("userId") Long userId);

    @Query("select e from GdprExport e where e.status = ro.mathlms.gdpr.GdprExport.Status.READY and e.expiresAt < :now")
    List<GdprExport> findReadyExpiredBefore(@Param("now") Instant now);

    /** Erasure: the archives are the person's data, every one of them goes. */
    @Modifying
    @Query("delete from GdprExport e where e.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
