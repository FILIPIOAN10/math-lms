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
 * A book / manual taught within a {@link SchoolClass}. Second level of the content
 * hierarchy (class → book → chapter → exercise). The link to the class is
 * unidirectional and LAZY so loading a class never drags in all its books.
 * A title is unique within its class, not globally.
 */
@Entity
@Table(name = "books", uniqueConstraints =
        @UniqueConstraint(name = "uk_books_class_title", columnNames = {"school_class_id", "title"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    public Book(SchoolClass schoolClass, String title, String description) {
        this.schoolClass = Objects.requireNonNull(schoolClass, "schoolClass");
        this.title = requireNonBlank(title, "title");
        this.description = description;
    }

    /** Updates the editable fields; the owning class does not change. */
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
