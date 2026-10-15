package ro.mathlms.enchapter;

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

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One EN chapter page ("A1 Ordinea efectuării operațiilor"): the theory photos and worked examples a student opens after
 * getting an EN question wrong. The short {@code code} is how questions point at it; {@code position} orders the list.
 */
@Entity
@Table(name = "en_chapters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnChapter {

    public static final int MAX_CODE = 10;
    public static final int MAX_TITLE = 200;
    /** A whole chapter of the book's theory is tens of thousands of characters; the page text may hold it. */
    public static final int MAX_NOTES = 60_000;
    public static final int MAX_BOOK_PAGE = 5000;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1," + MAX_CODE + "}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_CODE)
    private String code;

    @Column(nullable = false, length = MAX_TITLE)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EnArea area;

    @Column(nullable = false)
    private int position;

    /** Optional text above the photos, LaTeX allowed; {@code null} = none. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** The chapter this is a section (or a point) of; {@code null} = a chapter of its own. */
    @Column(name = "parent_id")
    private Long parentId;

    /** The page printed in the book where this starts; {@code null} = not known. */
    @Column(name = "book_page")
    private Integer bookPage;

    public EnChapter(String code, String title, EnArea area, int position, String notes) {
        this.position = position;
        update(code, title, area, notes);
    }

    /** Where this sits in the book; the rules about which parent is allowed belong to the service (it sees the tree). */
    public void placeIn(Long parentId, Integer bookPage) {
        if (bookPage != null && (bookPage < 1 || bookPage > MAX_BOOK_PAGE)) {
            throw new IllegalArgumentException("Pagina din carte e un număr între 1 și " + MAX_BOOK_PAGE + ".");
        }
        this.parentId = parentId;
        this.bookPage = bookPage;
    }

    /** Everything the teacher edits; the place in the list changes only through {@link #moveTo}. */
    public void update(String code, String title, EnArea area, String notes) {
        this.code = normalizeCode(code);
        this.title = requireTitle(title);
        this.area = Objects.requireNonNull(area, "area");
        String trimmed = notes == null ? null : notes.strip();
        if (trimmed != null && trimmed.length() > MAX_NOTES) {
            throw new IllegalArgumentException("Textul paginii poate avea cel mult " + MAX_NOTES + " de caractere.");
        }
        this.notes = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    /** " a12 " → "A12"; letters and digits only, at most ten. */
    public static String normalizeCode(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Codul are doar litere și cifre, fără spații (cel mult " + MAX_CODE + "), de exemplu A1 sau G9.");
        }
        return normalized;
    }

    private static String requireTitle(String title) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE) {
            throw new IllegalArgumentException("Titlul e obligatoriu și are cel mult " + MAX_TITLE + " de caractere.");
        }
        return trimmed;
    }
}
