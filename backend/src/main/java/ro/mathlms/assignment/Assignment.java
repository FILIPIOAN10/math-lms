package ro.mathlms.assignment;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import org.hibernate.annotations.BatchSize;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.Quiz;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Homework: an existing {@link Quiz} assigned to one {@link SchoolClass} (or to some of its students: catch-up
 * homework) with a due date. The deadline is soft - a
 * student can still hand in after it, and the hand-in is flagged late. Progress is not stored here; it is derived from
 * the student's graded-test attempts (see {@link AssignmentProgress}).
 */
@Entity
@Table(name = "assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** Only attempts started at or after this moment count towards the assignment. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When the "due soon" reminder was queued; null until then. Makes the reminder a once-only event. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    /**
     * Catch-up homework: the only students of the class it is for. Empty = the whole class. Batch-loaded, so a list of
     * homework does not load each one's students with a query of its own.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "assignment_students", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "student_id", nullable = false)
    @BatchSize(size = 50)
    private Set<Long> studentIds = new HashSet<>();

    public Assignment(Quiz quiz, SchoolClass schoolClass, Instant dueAt, Instant createdAt) {
        this.quiz = Objects.requireNonNull(quiz, "quiz");
        this.schoolClass = Objects.requireNonNull(schoolClass, "schoolClass");
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A moved deadline also re-arms the reminder: students get a fresh "due soon" for the new date. */
    public void reschedule(Instant newDueAt) {
        this.dueAt = Objects.requireNonNull(newDueAt, "dueAt");
        this.reminderSentAt = null;
    }

    public void markReminderSent(Instant when) {
        this.reminderSentAt = Objects.requireNonNull(when, "when");
    }

    /** Gives the homework to these students of its class only (none = back to the whole class). */
    public void restrictTo(Collection<Long> students) {
        studentIds.clear();
        studentIds.addAll(Objects.requireNonNull(students, "students"));
    }

    public Set<Long> getStudentIds() {
        return Set.copyOf(studentIds);
    }

    public boolean isForWholeClass() {
        return studentIds.isEmpty();
    }

    /** Whether the homework is for this student of its class: everyone in it, or one of the chosen few. */
    public boolean isFor(Long studentId) {
        return studentIds.isEmpty() || studentIds.contains(studentId);
    }

    public boolean isOverdue(Instant now) {
        return now.isAfter(dueAt);
    }
}
