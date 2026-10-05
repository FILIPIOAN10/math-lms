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
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.Quiz;

import java.time.Instant;
import java.util.Objects;

/**
 * Homework: an existing {@link Quiz} assigned to one {@link SchoolClass} with a due date. The deadline is soft - a
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

    public boolean isOverdue(Instant now) {
        return now.isAfter(dueAt);
    }
}
