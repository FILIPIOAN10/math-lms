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
 * One requirement of an OPEN problem, EN style: "(2p) a) Arată că x = 25." Its own points and barem, graded on its
 * own; the problem's statement, figure and photos are shared. Labelled by position: a), b), c)...
 */
@Entity
@Table(name = "quiz_item_parts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizItemPart {

    /** The most requirements one problem may have (EN has two or three). */
    public static final int MAX_PARTS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private QuizItem item;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "TEXT")
    private String solution;

    public QuizItemPart(QuizItem item, int position, String statement, int points, String solution) {
        if (Objects.requireNonNull(item, "item").getType() != QuizItemType.OPEN) {
            throw new IllegalArgumentException("only an open problem has requirements");
        }
        if (position < 0 || position >= MAX_PARTS) {
            throw new IllegalArgumentException("position must be 0.." + (MAX_PARTS - 1));
        }
        this.item = item;
        this.position = position;
        update(statement, solution);
        if (points < 0) {
            throw new IllegalArgumentException("points must not be negative");
        }
        this.points = points;
    }

    /** Text corrections only: the points are what grades were given against. */
    public void update(String statement, String solution) {
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("statement must not be blank");
        }
        this.statement = statement.strip();
        this.solution = solution == null || solution.isBlank() ? null : solution.strip();
    }

    /** "a", "b", "c"... */
    public String label() {
        return label(position);
    }

    public static String label(int position) {
        return String.valueOf((char) ('a' + position));
    }
}
