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

    // --- E3 hints ---

    @Test
    void noHintIsUsedByDefault() {
        QuizAttempt attempt = new QuizAttempt(new Quiz("Simulare EN", null),
                new ro.mathlms.user.User("elev@scoala.ro", "Elev Pop", ro.mathlms.user.Role.STUDENT));
        QuizItem open = new QuizItem(attempt.getQuiz(), 1, QuizItemType.OPEN, "s", 10, null);

        assertThat(new ItemResponse(attempt, open).getHintsUsed()).isZero();
    }

    @Test
    void revealingAHintRecordsHowFarTheStudentGot() {
        QuizAttempt attempt = new QuizAttempt(new Quiz("Simulare EN", null),
                new ro.mathlms.user.User("elev@scoala.ro", "Elev Pop", ro.mathlms.user.Role.STUDENT));
        ItemResponse response = new ItemResponse(attempt,
                new QuizItem(attempt.getQuiz(), 1, QuizItemType.OPEN, "s", 10, null));

        response.revealHint(1);
        response.revealHint(3);
        response.revealHint(2); // asking for an earlier hint again never lowers the count

        assertThat(response.getHintsUsed()).isEqualTo(3);
    }

    @Test
    void hintNumbersStartAtOne() {
        QuizAttempt attempt = new QuizAttempt(new Quiz("Simulare EN", null),
                new ro.mathlms.user.User("elev@scoala.ro", "Elev Pop", ro.mathlms.user.Role.STUDENT));
        ItemResponse response = new ItemResponse(attempt,
                new QuizItem(attempt.getQuiz(), 1, QuizItemType.OPEN, "s", 10, null));

        assertThatThrownBy(() -> response.revealHint(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gradeManualKeepsTheTeachersCommentTrimmed() {
        ItemResponse response = new ItemResponse(attempt, open());

        response.gradeManual(20, "  Bine, dar ai uitat unitatea de măsură.  ");

        assertThat(response.getAwardedPoints()).isEqualTo(20);
        assertThat(response.getTeacherComment()).isEqualTo("Bine, dar ai uitat unitatea de măsură.");
    }

    @Test
    void aBlankCommentIsStoredAsNoComment() {
        ItemResponse response = new ItemResponse(attempt, open());
        response.gradeManual(20, "Prima variantă");

        response.gradeManual(20, "   ");

        assertThat(response.getTeacherComment()).isNull();
    }
}
