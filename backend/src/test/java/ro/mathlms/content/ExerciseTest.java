package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExerciseTest {

    private final Chapter chapter =
            new Chapter(new Book(new SchoolClass("Clasa a 9-a", null), "M1", null), "Ecuații", null);

    @Test
    void createsWithChapterStatementAndOptionalFields() {
        Exercise ex = new Exercise(chapter, "Rezolvă $x+1=0$", "$x=-1$", Difficulty.EASY);

        assertThat(ex.getChapter()).isSameAs(chapter);
        assertThat(ex.getStatement()).isEqualTo("Rezolvă $x+1=0$");
        assertThat(ex.getSolution()).isEqualTo("$x=-1$");
        assertThat(ex.getDifficulty()).isEqualTo(Difficulty.EASY);
    }

    @Test
    void allowsNullSolutionAndDifficulty() {
        Exercise ex = new Exercise(chapter, "Rezolvă $x+1=0$", null, null);

        assertThat(ex.getSolution()).isNull();
        assertThat(ex.getDifficulty()).isNull();
    }

    @Test
    void rejectsNullChapter() {
        assertThatThrownBy(() -> new Exercise(null, "enunț", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("chapter");
    }

    @Test
    void rejectsBlankStatement() {
        assertThatThrownBy(() -> new Exercise(chapter, "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statement");
    }

    @Test
    void updateChangesEditableFields() {
        Exercise ex = new Exercise(chapter, "vechi", null, Difficulty.EASY);

        ex.update("nou", "$sol$", Difficulty.HARD);

        assertThat(ex.getStatement()).isEqualTo("nou");
        assertThat(ex.getSolution()).isEqualTo("$sol$");
        assertThat(ex.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(ex.getChapter()).isSameAs(chapter);
    }
}
