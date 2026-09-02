package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    private final SchoolClass ninthGrade = new SchoolClass("Clasa a 9-a", null);

    @Test
    void enrollsAStudentInAClass() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);

        Enrollment enrollment = new Enrollment(student, ninthGrade);

        assertThat(enrollment.getStudent()).isSameAs(student);
        assertThat(enrollment.getSchoolClass()).isSameAs(ninthGrade);
    }

    @Test
    void rejectsNonStudentAccount() {
        User parent = new User("parinte@scoala.ro", "Maria Pop", Role.PARENT);

        assertThatThrownBy(() -> new Enrollment(parent, ninthGrade))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STUDENT");
    }

    @Test
    void rejectsNulls() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);

        assertThatThrownBy(() -> new Enrollment(null, ninthGrade))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Enrollment(student, null))
                .isInstanceOf(NullPointerException.class);
    }
}
