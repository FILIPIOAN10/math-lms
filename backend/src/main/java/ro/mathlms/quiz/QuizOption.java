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

import java.util.Objects;

/**
 * One answer choice of a {@link QuizItemType#SINGLE_CHOICE} item. {@code position} orders
 * the choices (A, B, C…); {@code text} is LaTeX; {@code correct} marks the right one.
 * The "exactly one correct" rule is enforced by the service when saving an item's options.
 */
@Entity
@Table(name = "quiz_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private QuizItem item;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false)
    private boolean correct;

    /** The mistake behind this option, in the teacher's words, for the student who chose it; null = not written. */
    @Column(length = MAX_MISTAKE)
    private String mistake;

    /** The most characters an explanation may have - the column's length. */
    public static final int MAX_MISTAKE = 500;

    public QuizOption(QuizItem item, int position, String text, boolean correct) {
        this.item = Objects.requireNonNull(item, "item");
        this.position = position;
        this.text = requireNonBlank(text, "text");
        this.correct = correct;
    }

    /** Marks this choice right or wrong; the service keeps exactly one right per grilă and refuses it once taken. */
    void markCorrect(boolean correct) {
        this.correct = correct;
    }

    /** Fixes a typo in the choice without touching which one is correct (safe on a quiz students already took). */
    public void changeText(String text) {
        this.text = requireNonBlank(text, "text");
    }

    /** Says what mistake leads to this option; blank takes it away. Safe on a quiz students already took. */
    public void explainMistake(String mistake) {
        String trimmed = mistake == null ? null : mistake.strip();
        if (trimmed != null && trimmed.length() > MAX_MISTAKE) {
            throw new IllegalArgumentException("mistake must be at most " + MAX_MISTAKE + " characters");
        }
        this.mistake = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
