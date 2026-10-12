package ro.mathlms.grading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * One reusable grading comment of one teacher. Immutable once saved: "used once more" is a single UPDATE in the
 * repository (so two clicks at once never lose a count), and changing the text means deleting and saving anew.
 */
@Entity
@Table(name = "comment_snippets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentSnippet {

    /** The column's length. */
    public static final int MAX_TEXT = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = MAX_TEXT)
    private String text;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** {@code text} is already cleaned up (see {@link CommentSnippetService#clean}). */
    public CommentSnippet(Long teacherId, String text, Instant now) {
        this.teacherId = Objects.requireNonNull(teacherId, "teacherId");
        if (text == null || text.isBlank() || text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("snippet text must be 1.." + MAX_TEXT + " characters");
        }
        this.text = text;
        this.createdAt = now;
    }
}
