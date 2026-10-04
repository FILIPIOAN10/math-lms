package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import ro.mathlms.content.SchoolClass;

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
    void isForEveryoneUntilAssignedToAClass() {
        Quiz quiz = new Quiz("Simulare EN", null);
        SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);

        assertThat(quiz.getSchoolClass()).isNull();

        quiz.assignToClass(ninth);
        assertThat(quiz.getSchoolClass()).isSameAs(ninth);

        quiz.assignToClass(null);
        assertThat(quiz.getSchoolClass()).isNull();
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

    @Test
    void isUntimedByDefault() {
        assertThat(new Quiz("Simulare EN", null).getTimeLimitMinutes()).isNull();
    }

    @Test
    void changeTimeLimitSetsAndClearsIt() {
        Quiz quiz = new Quiz("Simulare EN", null);

        quiz.changeTimeLimit(45);
        assertThat(quiz.getTimeLimitMinutes()).isEqualTo(45);

        quiz.changeTimeLimit(null);
        assertThat(quiz.getTimeLimitMinutes()).isNull();
    }

    @Test
    void timeLimitMustBeBetweenOneMinuteAndTenHours() {
        Quiz quiz = new Quiz("Simulare EN", null);

        assertThatThrownBy(() -> quiz.changeTimeLimit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quiz.changeTimeLimit(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quiz.changeTimeLimit(601)).isInstanceOf(IllegalArgumentException.class);
        quiz.changeTimeLimit(1);
        quiz.changeTimeLimit(600);
    }

    // --- practice mode ---

    @Test
    void practiceIsNotAllowedByDefault() {
        assertThat(new Quiz("Simulare EN", null).isPracticeAllowed()).isFalse();
    }

    @Test
    void theTeacherCanAllowAndForbidPractice() {
        Quiz quiz = new Quiz("Simulare EN", null);

        quiz.allowPractice(true);
        assertThat(quiz.isPracticeAllowed()).isTrue();

        quiz.allowPractice(false);
        assertThat(quiz.isPracticeAllowed()).isFalse();
    }
}
