package ro.mathlms.quiz;

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
 * Extra graded attempts one student has at one test, on top of its {@link Quiz#getMaxAttempts() limit}: the teacher's
 * "+1 încercare" after a dropped connection or a wrong photo, without raising the limit for the whole class.
 */
@Entity
@Table(name = "attempt_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttemptGrant {

    /** The most a student can be given on one test (the database checks it too). */
    public static final int MAX_EXTRA = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "extra", nullable = false)
    private int extra;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** A first grant: one extra attempt. */
    public AttemptGrant(Quiz quiz, User student, Instant createdAt) {
        this.quiz = Objects.requireNonNull(quiz, "quiz");
        this.student = Objects.requireNonNull(student, "student");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.extra = 1;
    }

    public void addOne() {
        if (extra >= MAX_EXTRA) {
            throw new InvalidQuizException("Elevul are deja " + MAX_EXTRA + " încercări în plus la acest test.");
        }
        extra++;
    }

    /** The attempts a student may hand in: the test's limit plus their grants; null (no limit) stays null. */
    public static Integer allowed(Integer maxAttempts, int extra) {
        return maxAttempts == null ? null : maxAttempts + extra;
    }
}
