package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolClassTest {

    @Test
    void createsWithNameAndOptionalDescription() {
        SchoolClass ninthGrade = new SchoolClass("Clasa a 9-a", "Algebră și geometrie");

        assertThat(ninthGrade.getName()).isEqualTo("Clasa a 9-a");
        assertThat(ninthGrade.getDescription()).isEqualTo("Algebră și geometrie");
    }

    @Test
    void allowsNullDescription() {
        SchoolClass ninthGrade = new SchoolClass("Clasa a 9-a", null);

        assertThat(ninthGrade.getDescription()).isNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new SchoolClass("  ", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void updateChangesEditableFields() {
        SchoolClass ninthGrade = new SchoolClass("Clasa a 9-a", "vechi");

        ninthGrade.update("Clasa a 10-a", "nou");

        assertThat(ninthGrade.getName()).isEqualTo("Clasa a 10-a");
        assertThat(ninthGrade.getDescription()).isEqualTo("nou");
    }

    @Test
    void updateRejectsBlankName() {
        SchoolClass ninthGrade = new SchoolClass("Clasa a 9-a", null);

        assertThatThrownBy(() -> ninthGrade.update("", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
