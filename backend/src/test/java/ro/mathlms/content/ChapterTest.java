package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterTest {

    private final Book book = new Book(new SchoolClass("Clasa a 9-a", null), "Manual M1", null);

    @Test
    void createsWithBookTitleAndOptionalDescription() {
        Chapter chapter = new Chapter(book, "Ecuații", "de gradul I");

        assertThat(chapter.getBook()).isSameAs(book);
        assertThat(chapter.getTitle()).isEqualTo("Ecuații");
        assertThat(chapter.getDescription()).isEqualTo("de gradul I");
    }

    @Test
    void rejectsNullBook() {
        assertThatThrownBy(() -> new Chapter(null, "Ecuații", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("book");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new Chapter(book, "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void updateChangesEditableFields() {
        Chapter chapter = new Chapter(book, "vechi", "d1");

        chapter.update("nou", "d2");

        assertThat(chapter.getTitle()).isEqualTo("nou");
        assertThat(chapter.getDescription()).isEqualTo("d2");
        assertThat(chapter.getBook()).isSameAs(book);
    }
}
