package ro.mathlms.enchapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnChapterTest {

    @Test
    void keepsTheCodeShortAndUpperCaseAndTheTitleTrimmed() {
        EnChapter chapter = new EnChapter(" a12 ", "  Funcția liniară ", EnArea.ALGEBRA, 3, "  ");

        assertThat(chapter.getCode()).isEqualTo("A12");
        assertThat(chapter.getTitle()).isEqualTo("Funcția liniară");
        assertThat(chapter.getArea()).isEqualTo(EnArea.ALGEBRA);
        assertThat(chapter.getPosition()).isEqualTo(3);
        assertThat(chapter.getNotes()).isNull(); // blank notes = none
    }

    @Test
    void rejectsACodeWithSpacesSignsOrMoreThanTenCharacters() {
        assertThatThrownBy(() -> new EnChapter("A 1", "Titlu", EnArea.ALGEBRA, 1, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Codul");
        assertThatThrownBy(() -> new EnChapter("A-1", "Titlu", EnArea.ALGEBRA, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnChapter("ABCDEFGHIJK", "Titlu", EnArea.ALGEBRA, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnChapter("  ", "Titlu", EnArea.ALGEBRA, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankOrTooLongTitleAndAMissingArea() {
        assertThatThrownBy(() -> new EnChapter("A1", " ", EnArea.ALGEBRA, 1, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Titlul");
        assertThatThrownBy(() -> new EnChapter("A1", "x".repeat(201), EnArea.ALGEBRA, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnChapter("A1", "Titlu", null, 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anEditChangesEverythingButThePlaceInTheList() {
        EnChapter chapter = new EnChapter("A1", "Vechi", EnArea.ALGEBRA, 7, null);

        chapter.update("g1", "Segmente", EnArea.GEOMETRY, "Ce trebuie să știi");

        assertThat(chapter.getCode()).isEqualTo("G1");
        assertThat(chapter.getTitle()).isEqualTo("Segmente");
        assertThat(chapter.getArea()).isEqualTo(EnArea.GEOMETRY);
        assertThat(chapter.getNotes()).isEqualTo("Ce trebuie să știi");
        assertThat(chapter.getPosition()).isEqualTo(7);
    }

    @Test
    void anExampleNeedsAStatementAndAnExplainedSolution() {
        EnChapter chapter = new EnChapter("A1", "Ordinea operațiilor", EnArea.ALGEBRA, 1, null);

        EnChapterExample example = new EnChapterExample(chapter, 0, " 30 - 18 : 3 ", " = 24 ");

        assertThat(example.getStatement()).isEqualTo("30 - 18 : 3");
        assertThat(example.getSolution()).isEqualTo("= 24");
        assertThatThrownBy(() -> new EnChapterExample(chapter, 0, " ", "= 24"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Enunțul");
        assertThatThrownBy(() -> new EnChapterExample(chapter, 0, "30 - 18 : 3", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Rezolvarea");
    }

    @Test
    void anExampleFigureCanBeReplacedAndRemoved() {
        EnChapter chapter = new EnChapter("G1", "Segmente", EnArea.GEOMETRY, 1, null);
        EnChapterExample example = new EnChapterExample(chapter, 0, "AB = ?", "AB = 4 cm");

        assertThat(example.replaceImage("a.png")).isNull();
        assertThat(example.replaceImage("b.png")).isEqualTo("a.png");
        assertThat(example.removeImage()).isEqualTo("b.png");
        assertThat(example.getImageKey()).isNull();
    }
}
