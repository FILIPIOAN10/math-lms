package ro.mathlms.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ro.mathlms.content.SchoolClass;

/**
 * An assessment (e.g. "Simulare EN – Varianta 3") authored directly by an admin: it holds
 * an ordered list of {@link QuizItem}s. Built in {@link QuizStatus#DRAFT} and only visible
 * to students once {@link QuizStatus#PUBLISHED}.
 */
@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizStatus status = QuizStatus.DRAFT;

    /** The one class this quiz is for; {@code null} = every student may take it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_class_id")
    private SchoolClass schoolClass;

    /**
     * Minutes a student has once an attempt starts; {@code null} = untimed. Each attempt snapshots its own
     * deadline (see {@link QuizAttempt#getDeadlineAt()}), so changing this affects only attempts started later.
     */
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    public static final int MAX_TIME_LIMIT_MINUTES = 600;

    public Quiz(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    public void update(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    /** Sets the time limit in minutes (1–{@value #MAX_TIME_LIMIT_MINUTES}), or {@code null} for no limit. */
    public void changeTimeLimit(Integer minutes) {
        if (minutes != null && (minutes < 1 || minutes > MAX_TIME_LIMIT_MINUTES)) {
            throw new IllegalArgumentException(
                    "timeLimitMinutes must be between 1 and " + MAX_TIME_LIMIT_MINUTES + ", was " + minutes);
        }
        this.timeLimitMinutes = minutes;
    }

    /** Restricts the quiz to one class, or ({@code null}) opens it to every student again. */
    public void assignToClass(SchoolClass schoolClass) {
        this.schoolClass = schoolClass;
    }

    /** Makes the quiz visible to students. Idempotent-safe: publishing a published quiz is a no-op. */
    public void publish() {
        this.status = QuizStatus.PUBLISHED;
    }

    /** Pulls the quiz back to draft (hidden from students). */
    public void unpublish() {
        this.status = QuizStatus.DRAFT;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
