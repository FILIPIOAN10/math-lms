package ro.mathlms.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void createsUserWithValidData() {
        User user = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);

        assertThat(user.getEmail()).isEqualTo("ana@scoala.ro");
        assertThat(user.getFullName()).isEqualTo("Ana Pop");
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        assertThat(user.getId()).isNull(); // id vine abia după salvare în DB
    }

    @Test
    void rejectsBlankEmail() {
        assertThatThrownBy(() -> new User("  ", "Ana Pop", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsNullEmail() {
        assertThatThrownBy(() -> new User(null, "Ana Pop", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsBlankFullName() {
        assertThatThrownBy(() -> new User("ana@scoala.ro", "", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullName");
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(() -> new User("ana@scoala.ro", "Ana Pop", null))
                .isInstanceOf(NullPointerException.class);
    }
}