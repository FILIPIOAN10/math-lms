package ro.mathlms.quiz;

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

    public Quiz(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    public void update(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
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
