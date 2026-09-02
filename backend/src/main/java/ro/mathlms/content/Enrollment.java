package ro.mathlms.content;

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
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.Objects;

/**
 * Links a student to a {@link SchoolClass} they attend. A student is enrolled at most
 * once per class (unique constraint). Both links are LAZY. Only an account whose real
 * role is {@link Role#STUDENT} can be enrolled — the role is assigned on admin approval,
 * so this also keeps unapproved (roleless) accounts out.
 */
@Entity
@Table(name = "enrollments", uniqueConstraints =
        @UniqueConstraint(name = "uk_enrollments_student_class",
                columnNames = {"student_id", "school_class_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    public Enrollment(User student, SchoolClass schoolClass) {
        Objects.requireNonNull(student, "student");
        this.schoolClass = Objects.requireNonNull(schoolClass, "schoolClass");
        if (student.getRole() != Role.STUDENT) {
            throw new IllegalStateException(
                    "Only a STUDENT can be enrolled, was " + student.getRole());
        }
        this.student = student;
    }
}
