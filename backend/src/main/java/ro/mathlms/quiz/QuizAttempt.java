package ro.mathlms.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Instant;
import java.util.Objects;

/**
 * One student's sitting of a {@link Quiz}. Created {@link QuizAttemptStatus#IN_PROGRESS} when the
 * student starts; {@link #submit()} freezes the answers for grading; {@link #markGraded(int)} records
 * the final total once every {@link ItemResponse} has a score. Only a {@link Role#STUDENT} can attempt.
 */
@Entity
@Table(name = "quiz_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizAttemptStatus status = QuizAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Total awarded points across all items; null until {@link QuizAttemptStatus#GRADED}. */
    @Column
    private Integer score;

    public QuizAttempt(Quiz quiz, User student) {
        this.quiz = Objects.requireNonNull(quiz, "quiz");
        Objects.requireNonNull(student, "student");
        if (student.getRole() != Role.STUDENT) {
            throw new IllegalStateException(
                    "Only a STUDENT can attempt a quiz, was " + student.getRole());
        }
        this.student = student;
        this.startedAt = Instant.now();
    }

    /** Hands the attempt in for grading. Only valid while still in progress. */
    public void submit() {
        if (status != QuizAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress attempt can be submitted, was " + status);
        }
        this.status = QuizAttemptStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    /** Records the final total once every item is scored. Only valid after submission. */
    public void markGraded(int score) {
        if (status != QuizAttemptStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted attempt can be graded, was " + status);
        }
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        this.score = score;
        this.status = QuizAttemptStatus.GRADED;
    }
}
