package ro.mathlms.enchapter;

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

/** A worked example on a chapter page: the statement, its explained solution (both LaTeX) and an optional figure. */
@Entity
@Table(name = "en_chapter_examples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnChapterExample {

    public static final int MAX_TEXT = 8000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private EnChapter chapter;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String solution;

    /** The stored figure, or {@code null}. */
    @Column(name = "image_key", length = 200)
    private String imageKey;

    public EnChapterExample(EnChapter chapter, int position, String statement, String solution) {
        this.chapter = Objects.requireNonNull(chapter, "chapter");
        this.position = position;
        update(statement, solution);
    }

    public void update(String statement, String solution) {
        this.statement = require(statement, "Enunțul e obligatoriu.");
        this.solution = require(solution, "Rezolvarea e obligatorie: un model are explicația pas cu pas.");
    }

    /** Shows {@code key} as the figure and returns the one it replaces (to delete its file), or {@code null}. */
    public String replaceImage(String key) {
        String old = imageKey;
        this.imageKey = Objects.requireNonNull(key, "key");
        return old;
    }

    /** Takes the figure away and returns its stored name, or {@code null} when there was none. */
    public String removeImage() {
        String old = imageKey;
        this.imageKey = null;
        return old;
    }

    private static String require(String text, String whenMissing) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(whenMissing);
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Un text poate avea cel mult " + MAX_TEXT + " de caractere.");
        }
        return trimmed;
    }
}
