package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTest {

    private final SchoolClass ninthGrade = new SchoolClass("Clasa a 9-a", null);

    @Test
    void createsWithClassTitleAndOptionalDescription() {
        Book book = new Book(ninthGrade, "Manual M1", "Algebră");

        assertThat(book.getSchoolClass()).isSameAs(ninthGrade);
        assertThat(book.getTitle()).isEqualTo("Manual M1");
        assertThat(book.getDescription()).isEqualTo("Algebră");
    }

    @Test
    void rejectsNullClass() {
        assertThatThrownBy(() -> new Book(null, "Manual M1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("schoolClass");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new Book(ninthGrade, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void updateChangesEditableFields() {
        Book book = new Book(ninthGrade, "vechi", "d1");

        book.update("nou", "d2");

        assertThat(book.getTitle()).isEqualTo("nou");
        assertThat(book.getDescription()).isEqualTo("d2");
        assertThat(book.getSchoolClass()).isSameAs(ninthGrade);
    }
}
