package ro.mathlms.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A row of the teachers' journal (F1). Append-only: nothing updates or deletes one. People are plain ids, resolved to
 * names when the journal is read, so an erased account shows under its pseudonym everywhere.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

    static final int LABEL_MAX = 300;
    static final int DETAILS_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false, length = LABEL_MAX)
    private String label;

    @Column(length = DETAILS_MAX)
    private String details;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "related_user_id")
    private Long relatedUserId;

    AuditEntry(Instant occurredAt, Long actorId, AuditEvent event) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.action = event.action();
        this.entityId = event.entityId();
        String label = cut(event.label(), LABEL_MAX);
        this.label = label == null || label.isBlank() ? "—" : label;
        this.details = cut(event.details(), DETAILS_MAX);
        this.studentId = event.studentId();
        this.relatedUserId = event.relatedUserId();
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String flat = text.strip();
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "…";
    }
}
