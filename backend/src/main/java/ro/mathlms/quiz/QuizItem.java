package ro.mathlms.quiz;

import ro.mathlms.content.Chapter;
import ro.mathlms.enchapter.EnChapter;
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

    /** Overrides the quiz's chapter for this question (E6 mastery map); {@code null} = the quiz's chapter. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    /**
     * The EN chapter page chosen by the teacher for this question, or {@code null} = the default for its place on the
     * paper (see {@link EnChapterLinks}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "en_chapter_id")
    private EnChapter enChapter;

    /**
     * The heading this question sits under (EN style: "SUBIECTUL I", optionally followed on new lines by the
     * instruction under it), or {@code null}. Layout and numbering only - points and grading stay per question.
     */
    @Column(length = MAX_SECTION)
    private String section;

    /** The stored name of the picture shown under the statement, or {@code null}. */
    @Column(name = "image_key", length = 200)
    private String imageKey;

    /** Imported with "[imagine]" and still waiting for its picture; cleared when one is attached. */
    @Column(name = "image_required", nullable = false)
    private boolean imageRequired;

    /** The most characters a section heading (with its instruction) may have - the column's length. */
    public static final int MAX_SECTION = 300;

    /** The most hints one item may carry. */
    public static final int MAX_HINTS = 5;

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

    /** Tags this question with its own chapter, or ({@code null}) lets it inherit the quiz's. */
    public void tagChapter(Chapter chapter) {
        this.chapter = chapter;
    }

    /** Points this question at an EN chapter page, or ({@code null}) back at the default for its place. */
    public void pointToEnChapter(EnChapter chapter) {
        this.enChapter = chapter;
    }

    /** Shows {@code key}'s picture under the statement; the question no longer waits for one. */
    public void attachImage(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("image key must not be blank");
        }
        this.imageKey = key;
        this.imageRequired = false;
    }

    /** Takes the picture away and returns its stored name (to delete the file), or null when there was none. */
    public String removeImage() {
        String old = imageKey;
        this.imageKey = null;
        return old;
    }

    /** Marks a question that still needs its picture (import "[imagine]"). */
    public void requireImage(boolean required) {
        this.imageRequired = required && imageKey == null;
    }

    /** Puts this question under a heading; blank = no heading. */
    public void placeInSection(String section) {
        String trimmed = section == null ? null : section.strip();
        if (trimmed != null && trimmed.length() > MAX_SECTION) {
            throw new IllegalArgumentException("section must be at most " + MAX_SECTION + " characters");
        }
        this.section = trimmed == null || trimmed.isEmpty() ? null : trimmed;
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
