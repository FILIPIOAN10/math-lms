package ro.mathlms.audit;

import java.time.Instant;

/** A journal row with its people's names, as {@link AuditEntryRepository#findPage} reads it. */
public record AuditRow(Long id, Instant occurredAt, AuditAction action, String actorName, Long entityId, String label,
                       String details, Long studentId, String studentName, String relatedName) {
}
