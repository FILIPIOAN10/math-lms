package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizOptionTest {

    private final QuizItem item = new QuizItem(
            new Quiz("Simulare EN", null), 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 5, null);

    @Test
    void createsOption() {
        QuizOption option = new QuizOption(item, 0, "$4$", true);

        assertThat(option.getItem()).isSameAs(item);
        assertThat(option.getText()).isEqualTo("$4$");
        assertThat(option.isCorrect()).isTrue();
    }

    @Test
    void rejectsNullItem() {
        assertThatThrownBy(() -> new QuizOption(null, 0, "$4$", true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> new QuizOption(item, 0, " ", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }
}
