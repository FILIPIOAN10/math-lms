package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizAttemptTest {

    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final User student = new User("elev@scoala.ro", "Elev Pop", Role.STUDENT);

    @Test
    void startsInProgress() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);

        assertThat(attempt.getQuiz()).isSameAs(quiz);
        assertThat(attempt.getStudent()).isSameAs(student);
        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        assertThat(attempt.getStartedAt()).isNotNull();
        assertThat(attempt.getSubmittedAt()).isNull();
        assertThat(attempt.getScore()).isNull();
    }

    @Test
    void rejectsNonStudent() {
        User parent = new User("parinte@scoala.ro", "Parinte Pop", Role.PARENT);

        assertThatThrownBy(() -> new QuizAttempt(quiz, parent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STUDENT");
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new QuizAttempt(null, student))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QuizAttempt(quiz, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void submitFreezesTheAttempt() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);

        attempt.submit();

        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.SUBMITTED);
        assertThat(attempt.getSubmittedAt()).isNotNull();
    }

    @Test
    void cannotSubmitTwice() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        attempt.submit();

        assertThatThrownBy(attempt::submit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in-progress");
    }

    @Test
    void markGradedRecordsTheTotal() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        attempt.submit();

        attempt.markGraded(42);

        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(attempt.getScore()).isEqualTo(42);
    }

    @Test
    void cannotGradeBeforeSubmit() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);

        assertThatThrownBy(() -> attempt.markGraded(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submitted");
    }

    @Test
    void rejectsNegativeScore() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        attempt.submit();

        assertThatThrownBy(() -> attempt.markGraded(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score");
    }
}
