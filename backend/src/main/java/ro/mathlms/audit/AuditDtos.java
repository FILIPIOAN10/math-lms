package ro.mathlms.audit;

import java.time.Instant;
import java.util.List;

public final class AuditDtos {

    private AuditDtos() {
    }

    /**
     * {@code actorName} null = the account no longer exists (or nobody was signed in); {@code studentName} is whom the
     * change is about, {@code relatedName} a second person (the parent linked). Names are today's: an erased account
     * shows its pseudonym.
     */
    public record AuditEntryDto(Long id, Instant occurredAt, AuditAction action, AuditCategory category,
                                String actorName, Long entityId, String label, String details, Long studentId,
                                String studentName, String relatedName) {

        static AuditEntryDto from(AuditRow row) {
            return new AuditEntryDto(row.id(), row.occurredAt(), row.action(), row.action().category(),
                    row.actorName(), row.entityId(), row.label(), row.details(), row.studentId(), row.studentName(),
                    row.relatedName());
        }
    }

    /** {@code nextBefore}: pass it as {@code before} for the next (older) page; null = this was the last one. */
    public record AuditPageDto(List<AuditEntryDto> entries, Long nextBefore) {
    }
}
