package ro.mathlms.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Top of the content hierarchy: a class / study year (e.g. "Clasa a 9-a"). Students
 * enroll into a {@code SchoolClass}; it groups the {@code Book}s taught at that level.
 * Named {@code SchoolClass} rather than {@code Class} to avoid clashing with
 * {@link java.lang.Class}.
 */
@Entity
@Table(name = "school_classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    public SchoolClass(String name, String description) {
        this.name = requireNonBlank(name, "name");
        this.description = description;
    }

    /** Updates the editable fields (name stays unique across classes). */
    public void update(String name, String description) {
        this.name = requireNonBlank(name, "name");
        this.description = description;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
