package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizItemTest {

    private final Quiz quiz = new Quiz("Simulare EN", null);

    @Test
    void createsSingleChoiceItem() {
        QuizItem item = new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 5, null);

        assertThat(item.getQuiz()).isSameAs(quiz);
        assertThat(item.getType()).isEqualTo(QuizItemType.SINGLE_CHOICE);
        assertThat(item.getPosition()).isEqualTo(1);
        assertThat(item.getPoints()).isEqualTo(5);
    }

    @Test
    void createsOpenItemWithBarem() {
        QuizItem item = new QuizItem(quiz, 3, QuizItemType.OPEN, "Rezolvă ecuația.", 30, "barem…");

        assertThat(item.getType()).isEqualTo(QuizItemType.OPEN);
        assertThat(item.getSolution()).isEqualTo("barem…");
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new QuizItem(null, 1, QuizItemType.OPEN, "x", 1, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QuizItem(quiz, 1, null, "x", 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankStatement() {
        assertThatThrownBy(() -> new QuizItem(quiz, 1, QuizItemType.OPEN, "  ", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statement");
    }

    @Test
    void rejectsNegativePoints() {
        assertThatThrownBy(() -> new QuizItem(quiz, 1, QuizItemType.OPEN, "x", -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points");
    }

    @Test
    void updateChangesFields() {
        QuizItem item = new QuizItem(quiz, 1, QuizItemType.OPEN, "vechi", 5, null);

        item.update(2, "nou", 10, "barem");

        assertThat(item.getPosition()).isEqualTo(2);
        assertThat(item.getStatement()).isEqualTo("nou");
        assertThat(item.getPoints()).isEqualTo(10);
        assertThat(item.getSolution()).isEqualTo("barem");
    }

    @Test
    void aSectionHeadingIsTrimmedAndABlankOneMeansNone() {
        QuizItem item = new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "x", 5, null);

        item.placeInSection("  SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.  ");
        assertThat(item.getSection()).isEqualTo("SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.");

        item.placeInSection("   ");
        assertThat(item.getSection()).isNull();
    }

    @Test
    void aSectionHeadingHasALimit() {
        QuizItem item = new QuizItem(quiz, 0, QuizItemType.OPEN, "x", 5, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> item.placeInSection("x".repeat(QuizItem.MAX_SECTION + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
