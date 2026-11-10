package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Who may start another graded attempt, from their attempts at the quiz. */
class AttemptAllowanceTest {

    private final Instant now = Instant.parse("2026-11-01T10:00:00Z");
    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final User student = new User("elev@scoala.ro", "Elev Pop", Role.STUDENT);

    private QuizAttempt open(Instant startedAt) {
        QuizAttempt attempt = new QuizAttempt(quiz, student, startedAt);
        ReflectionTestUtils.setField(attempt, "id", 1L);
        return attempt;
    }

    private QuizAttempt waiting(Instant startedAt) {
        QuizAttempt attempt = open(startedAt);
        attempt.submit();
        return attempt;
    }

    private QuizAttempt graded(Instant startedAt) {
        QuizAttempt attempt = waiting(startedAt);
        attempt.markGraded(5);
        return attempt;
    }

    private QuizAttempt practice() {
        QuizAttempt attempt = new QuizAttempt(quiz, student, now, AttemptMode.PRACTICE);
        attempt.submit();
        attempt.completePractice();
        return attempt;
    }

    @Test
    void aTestNeverTakenCanBeStarted() {
        AttemptAllowance allowance = AttemptAllowance.of(1, List.of(), null);

        assertThat(allowance.used()).isZero();
        assertThat(allowance.blockedBy()).isNull();
    }

    @Test
    void theLastAttemptHandedInClosesTheTest() {
        assertThat(AttemptAllowance.of(1, List.of(graded(now)), null).blockedBy()).isEqualTo(RetakeBlock.NO_ATTEMPTS_LEFT);
    }

    @Test
    void attemptsLeftAllowARetake() {
        AttemptAllowance allowance = AttemptAllowance.of(3, List.of(graded(now), graded(now)), null);

        assertThat(allowance.used()).isEqualTo(2);
        assertThat(allowance.blockedBy()).isNull();
    }

    /** What hides the answer key: another attempt to come, now or once the paper waiting for its grade is graded. */
    @Test
    void aRetakeIsLeftUntilTheLastAttemptIsHandedIn() {
        assertThat(AttemptAllowance.of(2, List.of(graded(now)), null).retakeLeft()).isTrue();
        assertThat(AttemptAllowance.of(2, List.of(waiting(now)), null).retakeLeft()).isTrue(); // blocked only for now
        assertThat(AttemptAllowance.of(null, List.of(graded(now), graded(now)), null).retakeLeft()).isTrue();
        assertThat(AttemptAllowance.of(1, List.of(waiting(now)), null).retakeLeft()).isFalse();
        assertThat(AttemptAllowance.of(1, List.of(graded(now), practice()), null).retakeLeft()).isFalse();
        // homework given after the paper starts the count again: one more attempt to come
        assertThat(AttemptAllowance.of(1, List.of(graded(now)), now.plus(Duration.ofDays(1))).retakeLeft()).isTrue();
    }

    @Test
    void noLimitMeansAlwaysAnotherAttempt() {
        assertThat(AttemptAllowance.of(null, List.of(graded(now), graded(now), graded(now)), null).blockedBy()).isNull();
    }

    @Test
    void aPaperWaitingForItsGradeBlocksARetakeEvenWithNoLimit() {
        assertThat(AttemptAllowance.of(null, List.of(waiting(now)), null).blockedBy()).isEqualTo(RetakeBlock.WAITING_FOR_GRADE);
    }

    @Test
    void waitingForTheGradeIsSaidBeforeRunningOutOfAttempts() {
        assertThat(AttemptAllowance.of(1, List.of(waiting(now)), null).blockedBy()).isEqualTo(RetakeBlock.WAITING_FOR_GRADE);
    }

    @Test
    void anOpenAttemptIsResumedAndUsesNothing() {
        AttemptAllowance allowance = AttemptAllowance.of(1, List.of(open(now)), null);

        assertThat(allowance.used()).isZero();
        assertThat(allowance.blockedBy()).isNull();
    }

    @Test
    void anOpenAttemptIsResumedEvenIfTheTeacherLoweredTheLimitMeanwhile() {
        assertThat(AttemptAllowance.of(1, List.of(graded(now), open(now)), null).blockedBy()).isNull();
    }

    @Test
    void aPracticeNeverUsesAnAttempt() {
        AttemptAllowance allowance = AttemptAllowance.of(1, List.of(practice()), null);

        assertThat(allowance.used()).isZero();
        assertThat(allowance.blockedBy()).isNull();
    }

    @Test
    void homeworkStartsTheCountAgain() {
        Instant given = now.plus(Duration.ofDays(1));

        AttemptAllowance allowance = AttemptAllowance.of(1, List.of(graded(now)), given);

        assertThat(allowance.used()).isZero();
        assertThat(allowance.blockedBy()).isNull();
    }

    @Test
    void aPaperWaitingFromBeforeTheHomeworkDoesNotBlockIt() {
        assertThat(AttemptAllowance.of(null, List.of(waiting(now)), now.plus(Duration.ofDays(1))).blockedBy()).isNull();
    }

    @Test
    void anAttemptStartedSinceTheHomeworkWasGivenCounts() {
        Instant given = now.minus(Duration.ofDays(1));

        assertThat(AttemptAllowance.of(1, List.of(graded(now)), given).blockedBy()).isEqualTo(RetakeBlock.NO_ATTEMPTS_LEFT);
    }
}
