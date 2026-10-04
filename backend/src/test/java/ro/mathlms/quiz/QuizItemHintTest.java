package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizItemHintTest {

    private final QuizItem item = new QuizItem(new Quiz("Simulare EN", null), 1, QuizItemType.OPEN, "s", 10, null);

    @Test
    void holdsItsItemPositionAndText() {
        QuizItemHint hint = new QuizItemHint(item, 1, "Incearca sa dai factor comun");

        assertThat(hint.getItem()).isSameAs(item);
        assertThat(hint.getPosition()).isEqualTo(1);
        assertThat(hint.getText()).isEqualTo("Incearca sa dai factor comun");
    }

    @Test
    void positionsStartAtOne() {
        assertThatThrownBy(() -> new QuizItemHint(item, 0, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuizItemHint(item, -1, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHintNeedsText() {
        assertThatThrownBy(() -> new QuizItemHint(item, 1, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuizItemHint(item, 1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHintNeedsAnItem() {
        assertThatThrownBy(() -> new QuizItemHint(null, 1, "x")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anItemAllowsAtMostFiveHints() {
        assertThat(QuizItem.MAX_HINTS).isEqualTo(5);
    }
}
