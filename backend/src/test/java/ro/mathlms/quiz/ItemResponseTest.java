package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemResponseTest {

    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final User student = new User("elev@scoala.ro", "Elev Pop", Role.STUDENT);
    private final QuizAttempt attempt = new QuizAttempt(quiz, student);

    private QuizItem singleChoice() {
        return new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 5, null);
    }

    private QuizItem open() {
        return new QuizItem(quiz, 2, QuizItemType.OPEN, "Rezolvă ecuația.", 30, "barem");
    }

    @Test
    void createsBlankResponse() {
        QuizItem item = singleChoice();
        ItemResponse response = new ItemResponse(attempt, item);

        assertThat(response.getAttempt()).isSameAs(attempt);
        assertThat(response.getItem()).isSameAs(item);
        assertThat(response.getSelectedOption()).isNull();
        assertThat(response.getImageKey()).isNull();
        assertThat(response.getAwardedPoints()).isNull();
        assertThat(response.getCorrect()).isNull();
    }

    @Test
    void rejectsNulls() {
        QuizItem item = singleChoice();
        assertThatThrownBy(() -> new ItemResponse(null, item))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ItemResponse(attempt, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void answerSingleChoiceRecordsTheOption() {
        QuizItem item = singleChoice();
        QuizOption a = new QuizOption(item, 0, "4", true);
        ItemResponse response = new ItemResponse(attempt, item);

        response.answerSingleChoice(a);

        assertThat(response.getSelectedOption()).isSameAs(a);
        assertThat(response.getImageKey()).isNull();
    }

    @Test
    void answerSingleChoiceRejectsOnOpenItem() {
        QuizItem item = open();
        QuizOption stray = new QuizOption(singleChoice(), 0, "4", true);
        ItemResponse response = new ItemResponse(attempt, item);

        assertThatThrownBy(() -> response.answerSingleChoice(stray))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SINGLE_CHOICE");
    }

    @Test
    void answerOpenRecordsTheImageKey() {
        QuizItem item = open();
        ItemResponse response = new ItemResponse(attempt, item);

        response.answerOpen("uploads/rezolvare-1.jpg");

        assertThat(response.getImageKey()).isEqualTo("uploads/rezolvare-1.jpg");
        assertThat(response.getSelectedOption()).isNull();
    }

    @Test
    void answerOpenRejectsOnSingleChoiceItem() {
        ItemResponse response = new ItemResponse(attempt, singleChoice());

        assertThatThrownBy(() -> response.answerOpen("x.jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void answerOpenRejectsBlankImageKey() {
        ItemResponse response = new ItemResponse(attempt, open());

        assertThatThrownBy(() -> response.answerOpen("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imageKey");
    }

    @Test
    void gradeAutoRecordsCorrectnessAndPoints() {
        QuizItem item = singleChoice();
        ItemResponse response = new ItemResponse(attempt, item);

        response.gradeAuto(true, 5);

        assertThat(response.getCorrect()).isTrue();
        assertThat(response.getAwardedPoints()).isEqualTo(5);
    }

    @Test
    void gradeManualRecordsTeacherPoints() {
        ItemResponse response = new ItemResponse(attempt, open());

        response.gradeManual(25);

        assertThat(response.getAwardedPoints()).isEqualTo(25);
        assertThat(response.getCorrect()).isNull();
    }

    @Test
    void gradeRejectsNegativePoints() {
        ItemResponse response = new ItemResponse(attempt, singleChoice());

        assertThatThrownBy(() -> response.gradeAuto(false, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("awardedPoints");
    }
}
