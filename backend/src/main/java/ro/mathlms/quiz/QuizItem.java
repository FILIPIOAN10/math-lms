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

import java.util.Objects;

/**
 * One subiect within a {@link Quiz}. {@code position} fixes the order (s1, s2, s3…);
 * {@code type} decides how it is answered and graded. {@code statement} is LaTeX.
 * {@code points} is the maximum score for this item. {@code solution} is an optional
 * barem / reference answer (mainly for OPEN items the teacher grades by hand).
 */
@Entity
@Table(name = "quiz_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizItemType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "TEXT")
    private String solution;

    public QuizItem(Quiz quiz, int position, QuizItemType type, String statement,
                    int points, String solution) {
        this.quiz = Objects.requireNonNull(quiz, "quiz");
        this.type = Objects.requireNonNull(type, "type");
        this.statement = requireNonBlank(statement, "statement");
        this.position = position;
        this.points = requireNonNegative(points);
        this.solution = solution;
    }

    /** Updates the editable fields; the owning quiz and the type do not change here. */
    public void update(int position, String statement, int points, String solution) {
        this.position = position;
        this.statement = requireNonBlank(statement, "statement");
        this.points = requireNonNegative(points);
        this.solution = solution;
    }

    private static int requireNonNegative(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
        return points;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
