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

/**
 * Extra graded attempts one student has at one test, on top of its {@link Quiz#getMaxAttempts() limit}: the teacher's
 * "+1 încercare" after a dropped connection or a wrong photo, without raising the limit for the whole class. Rows are
 * written only by {@link AttemptGrantRepository#addOne}, one atomic upsert, so two grants at once both count.
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

    /** The attempts a student may hand in: the test's limit plus their grants; null (no limit) stays null. */
    public static Integer allowed(Integer maxAttempts, int extra) {
        return maxAttempts == null ? null : maxAttempts + extra;
    }
}
