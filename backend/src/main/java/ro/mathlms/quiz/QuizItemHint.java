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
 * One progressive hint of a quiz item (LaTeX text). {@code position} starts at 1 and orders the hints: a student in
 * practice mode reveals them in that order, one at a time, before reaching the full solution.
 */
@Entity
@Table(name = "quiz_item_hints")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizItemHint {

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

    public QuizItemHint(QuizItem item, int position, String text) {
        this.item = Objects.requireNonNull(item, "item");
        if (position < 1) {
            throw new IllegalArgumentException("position must start at 1, was " + position);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        this.position = position;
        this.text = text;
    }
}
