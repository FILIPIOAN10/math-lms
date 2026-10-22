package ro.mathlms.invite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {

    Optional<InviteLink> findByCode(String code);

    /** A class's links that are neither revoked nor expired (full ones included, so the teacher sees them), newest first. */
    @Query("select l from InviteLink l join fetch l.schoolClass where l.schoolClass.id = :classId"
            + " and l.revokedAt is null and l.expiresAt > :now order by l.createdAt desc, l.id desc")
    List<InviteLink> findActiveForClass(@Param("classId") Long classId, @Param("now") Instant now);

    /** A student's parent links still waiting to be used — replaced when the teacher mints a new one. */
    @Query("select l from InviteLink l where l.student.id = :studentId"
            + " and l.revokedAt is null and l.expiresAt > :now and l.uses < l.maxUses")
    List<InviteLink> findOpenForStudent(@Param("studentId") Long studentId, @Param("now") Instant now);

    /**
     * Takes one use of the link if it is still usable. The check and the increment are one statement, so two people
     * joining at once can never push a link past its cap: 1 = claimed, 0 = revoked, expired or full by now.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update InviteLink l set l.uses = l.uses + 1 where l.id = :id"
            + " and l.revokedAt is null and l.expiresAt > :now and l.uses < l.maxUses")
    int claimUse(@Param("id") Long id, @Param("now") Instant now);

    /** The links of one kind that still let someone in: not revoked, not expired, not used up. Newest first. */
    @Query("select l from InviteLink l where l.kind = :kind"
            + " and l.revokedAt is null and l.expiresAt > :now and l.uses < l.maxUses"
            + " order by l.createdAt desc, l.id desc")
    List<InviteLink> findActiveByKind(@Param("kind") InviteLinkKind kind, @Param("now") Instant now);
}
