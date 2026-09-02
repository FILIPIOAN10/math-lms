package ro.mathlms.content;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * A single exercise (leaf of the content hierarchy) inside a {@link Chapter}.
 * {@code statement} and {@code solution} hold LaTeX so the frontend can render them
 * with KaTeX. Carries a JPA {@link Version} for optimistic locking: two admins editing
 * the same exercise get a conflict on save (→ 409) instead of a silent last-write-wins.
 */
@Entity
@Table(name = "exercises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    /** The problem text, as LaTeX. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    /** Worked answer / solution, as LaTeX; optional. */
    @Column(columnDefinition = "TEXT")
    private String solution;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Difficulty difficulty;

    @Version
    @Column(nullable = false)
    private long version;

    public Exercise(Chapter chapter, String statement, String solution, Difficulty difficulty) {
        this.chapter = Objects.requireNonNull(chapter, "chapter");
        this.statement = requireNonBlank(statement, "statement");
        this.solution = solution;
        this.difficulty = difficulty;
    }

    /** Updates the editable fields; the owning chapter does not change. */
    public void update(String statement, String solution, Difficulty difficulty) {
        this.statement = requireNonBlank(statement, "statement");
        this.solution = solution;
        this.difficulty = difficulty;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
