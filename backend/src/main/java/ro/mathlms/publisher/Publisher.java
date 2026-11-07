package ro.mathlms.publisher;

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

/**
 * A publishing house a test may come from (V53). Only administrators add them; none is built in. The logo, if any, is
 * a stored image file named by {@link #logoKey}.
 */
@Entity
@Table(name = "publishers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publisher {

    public static final int NAME_MAX = 120;
    public static final int DESCRIPTION_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @Column(length = DESCRIPTION_MAX)
    private String description;

    @Column(name = "logo_key", length = 200)
    private String logoKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Publisher(String name, String description, Instant now) {
        rename(name, description, now);
        this.createdAt = now;
    }

    /** The name trimmed and required; a blank description is none. */
    void rename(String name, String description, Instant now) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Scrie numele editurii.");
        }
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("Numele editurii are cel mult " + NAME_MAX + " de caractere.");
        }
        String text = description == null || description.isBlank() ? null : description.strip();
        if (text != null && text.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("Descrierea are cel mult " + DESCRIPTION_MAX + " de caractere.");
        }
        this.name = trimmed;
        this.description = text;
        this.updatedAt = now;
    }

    /** Sets (or, with null, removes) the logo; returns the key it replaces, for the caller to delete. */
    String changeLogo(String key, Instant now) {
        String old = logoKey;
        this.logoKey = key;
        this.updatedAt = now;
        return old;
    }
}
