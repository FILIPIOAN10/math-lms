package ro.mathlms.publisher;

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

import java.time.Instant;
import java.util.Objects;

/** A book of a publisher ("Culegere EN 2026", V54): the tests taken from it name it. Only administrators add them. */
@Entity
@Table(name = "publisher_books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublisherBook {

    public static final int TITLE_MAX = 200;
    public static final int DESCRIPTION_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PublisherBook(Publisher publisher, String title, String description, Instant now) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        rename(title, description, now);
        this.createdAt = now;
    }

    /** The title trimmed and required; a blank description is none. */
    void rename(String title, String description, Instant now) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Scrie titlul cărții.");
        }
        if (trimmed.length() > TITLE_MAX) {
            throw new IllegalArgumentException("Titlul cărții are cel mult " + TITLE_MAX + " de caractere.");
        }
        String text = description == null || description.isBlank() ? null : description.strip();
        if (text != null && text.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("Descrierea are cel mult " + DESCRIPTION_MAX + " de caractere.");
        }
        this.title = trimmed;
        this.description = text;
        this.updatedAt = now;
    }
}
