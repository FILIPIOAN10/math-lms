package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Duration;
import java.time.Instant;

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

    // --- server-side timer ---

    @Test
    void anUntimedQuizGivesAnAttemptNoDeadline() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);

        assertThat(attempt.getDeadlineAt()).isNull();
        assertThat(attempt.isOverdue(Instant.now().plus(Duration.ofDays(30)), Duration.ZERO)).isFalse();
    }

    @Test
    void aTimedQuizSnapshotsTheDeadlineAtStart() {
        quiz.changeTimeLimit(45);

        QuizAttempt attempt = new QuizAttempt(quiz, student);

        assertThat(attempt.getDeadlineAt()).isEqualTo(attempt.getStartedAt().plus(Duration.ofMinutes(45)));
    }

    @Test
    void changingTheQuizLimitLaterDoesNotMoveARunningAttemptsDeadline() {
        quiz.changeTimeLimit(45);
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        Instant before = attempt.getDeadlineAt();

        quiz.changeTimeLimit(5);

        assertThat(attempt.getDeadlineAt()).isEqualTo(before);
    }

    @Test
    void isOverdueOnlyAfterTheDeadlinePlusGrace() {
        quiz.changeTimeLimit(10);
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        Instant deadline = attempt.getDeadlineAt();
        Duration grace = Duration.ofSeconds(30);

        assertThat(attempt.isOverdue(deadline.minusSeconds(1), grace)).isFalse();
        assertThat(attempt.isOverdue(deadline.plusSeconds(30), grace)).isFalse();   // exactly at the edge: still allowed
        assertThat(attempt.isOverdue(deadline.plusSeconds(31), grace)).isTrue();
    }

    // --- practice mode ---

    @Test
    void anAttemptIsATestByDefault() {
        assertThat(new QuizAttempt(quiz, student).getMode()).isEqualTo(AttemptMode.TEST);
    }

    @Test
    void aPracticeAttemptHasNoDeadlineEvenOnATimedQuiz() {
        quiz.changeTimeLimit(45);

        QuizAttempt practice = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.PRACTICE);

        assertThat(practice.getMode()).isEqualTo(AttemptMode.PRACTICE);
        assertThat(practice.getDeadlineAt()).isNull();
        assertThat(practice.isOverdue(Instant.now().plus(Duration.ofDays(30)), Duration.ZERO)).isFalse();
    }

    @Test
    void aTestAttemptStillGetsItsDeadline() {
        quiz.changeTimeLimit(45);

        QuizAttempt test = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.TEST);

        assertThat(test.getDeadlineAt()).isNotNull();
    }

    @Test
    void finishingAPracticeEndsItGradedButWithoutAScore() {
        QuizAttempt practice = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.PRACTICE);
        practice.submit();

        practice.completePractice();

        assertThat(practice.getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(practice.getScore()).isNull(); // practice is never marked: "fara nota"
    }

    @Test
    void onlyASubmittedPracticeCanBeCompleted() {
        QuizAttempt practice = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.PRACTICE);

        assertThatThrownBy(practice::completePractice).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTestAttemptCannotBeCompletedAsPractice() {
        QuizAttempt test = new QuizAttempt(quiz, student);
        test.submit();

        assertThatThrownBy(test::completePractice).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTeacherCanCommentOnASubmittedOrGradedTest() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        attempt.submit();
        attempt.commentOverall("  Lucrare îngrijită.  ");
        assertThat(attempt.getTeacherComment()).isEqualTo("Lucrare îngrijită.");

        attempt.markGraded(10);
        attempt.commentOverall("Felicitări!");
        assertThat(attempt.getTeacherComment()).isEqualTo("Felicitări!");

        attempt.commentOverall(" ");
        assertThat(attempt.getTeacherComment()).isNull();
    }

    @Test
    void thereIsNothingToCommentOnBeforeTheStudentHandsIn() {
        QuizAttempt attempt = new QuizAttempt(quiz, student);

        assertThatThrownBy(() -> attempt.commentOverall("Prea devreme"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPracticeSessionTakesNoTeacherComment() {
        QuizAttempt practice = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.PRACTICE);
        practice.submit();

        assertThatThrownBy(() -> practice.commentOverall("Nu se notează"))
                .isInstanceOf(IllegalStateException.class);
    }
}
