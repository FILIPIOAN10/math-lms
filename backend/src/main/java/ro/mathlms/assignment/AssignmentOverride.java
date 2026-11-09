package ro.mathlms.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.mathlms.user.User;

import java.time.Instant;
import java.util.Objects;

/**
 * One student's exception to a homework: their own deadline ({@code dueAt}), or excused - the homework does not count
 * for them. Everyone else keeps the class deadline of the {@link Assignment}. It always says something: a deadline, an
 * excuse, or both (an excused student keeps the date the teacher gave them, should the excuse be taken back).
 */
@Entity
@Table(name = "assignment_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignmentOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /** The student's own deadline; null = the class deadline. */
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "excused", nullable = false)
    private boolean excused;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AssignmentOverride(Assignment assignment, User student, Instant createdAt) {
        this.assignment = Objects.requireNonNull(assignment, "assignment");
        this.student = Objects.requireNonNull(student, "student");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public void moveDeadline(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public void excuse(boolean excused) {
        this.excused = excused;
    }

    /** Neither a deadline nor an excuse: the row would mean nothing and is removed instead. */
    public boolean isEmpty() {
        return dueAt == null && !excused;
    }

    /** The deadline this student works to: their own, else the class one. */
    public static Instant dueAtFor(Assignment assignment, AssignmentOverride override) {
        return override != null && override.dueAt != null ? override.dueAt : assignment.getDueAt();
    }

    public static boolean isExcused(AssignmentOverride override) {
        return override != null && override.excused;
    }
}
