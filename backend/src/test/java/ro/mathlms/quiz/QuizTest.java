package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizTest {

    @Test
    void createsAsDraft() {
        Quiz quiz = new Quiz("Simulare EN", "Varianta 3");

        assertThat(quiz.getTitle()).isEqualTo("Simulare EN");
        assertThat(quiz.getDescription()).isEqualTo("Varianta 3");
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.DRAFT);
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new Quiz("  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void publishAndUnpublishToggleStatus() {
        Quiz quiz = new Quiz("Simulare EN", null);

        quiz.publish();
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);

        quiz.unpublish();
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.DRAFT);
    }

    @Test
    void updateChangesFields() {
        Quiz quiz = new Quiz("vechi", "d1");

        quiz.update("nou", "d2");

        assertThat(quiz.getTitle()).isEqualTo("nou");
        assertThat(quiz.getDescription()).isEqualTo("d2");
    }
}
