package ro.mathlms.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * A chapter within a {@link Book}. Third level of the content hierarchy
 * (class → book → chapter → exercise). The link to the book is unidirectional
 * and LAZY; a title is unique within its book.
 */
@Entity
@Table(name = "chapters", uniqueConstraints =
        @UniqueConstraint(name = "uk_chapters_book_title", columnNames = {"book_id", "title"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    public Chapter(Book book, String title, String description) {
        this.book = Objects.requireNonNull(book, "book");
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    /** Updates the editable fields; the owning book does not change. */
    public void update(String title, String description) {
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
