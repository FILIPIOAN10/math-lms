package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.assignment.AssignmentProgress.State;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** How far a student got on an assignment, derived from their graded-test attempts at the quiz. */
class AssignmentProgressTest {

    private final Instant created = Instant.parse("2026-10-05T10:00:00Z");
    private final Instant due = created.plus(Duration.ofDays(3));
    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final User student = new User("elev@scoala.ro", "Elev Pop", Role.STUDENT);
    private final Assignment assignment = new Assignment(quiz, new SchoolClass("Clasa a 9-a", null), due, created);

    private QuizAttempt attempt(long id, AttemptMode mode, Instant startedAt) {
        QuizAttempt attempt = new QuizAttempt(quiz, student, startedAt, mode);
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }

    private QuizAttempt submitted(long id, Instant submittedAt) {
        QuizAttempt attempt = attempt(id, AttemptMode.TEST, created.plusSeconds(60));
        attempt.submit();
        ReflectionTestUtils.setField(attempt, "submittedAt", submittedAt);
        return attempt;
    }

    private QuizAttempt graded(long id, Instant submittedAt, int score) {
        QuizAttempt attempt = submitted(id, submittedAt);
        attempt.markGraded(score);
        return attempt;
    }

    @Test
    void noAttemptsMeansNotStarted() {
        AssignmentProgress progress = AssignmentProgress.of(assignment, List.of());

        assertThat(progress.state()).isEqualTo(State.NOT_STARTED);
        assertThat(progress.attemptId()).isNull();
        assertThat(progress.late()).isFalse();
    }

    @Test
    void anOpenTestAttemptMeansInProgress() {
        QuizAttempt open = attempt(7L, AttemptMode.TEST, created.plusSeconds(120));

        AssignmentProgress progress = AssignmentProgress.of(assignment, List.of(open));

        assertThat(progress.state()).isEqualTo(State.IN_PROGRESS);
        assertThat(progress.attemptId()).isEqualTo(7L);
    }

    @Test
    void aHandedInTestOnTimeIsDoneAndNotLate() {
        QuizAttempt done = submitted(8L, due.minus(Duration.ofHours(5)));

        AssignmentProgress progress = AssignmentProgress.of(assignment, List.of(done));

        assertThat(progress.state()).isEqualTo(State.SUBMITTED);
        assertThat(progress.late()).isFalse();
        assertThat(progress.submittedAt()).isEqualTo(done.getSubmittedAt());
        assertThat(progress.score()).isNull();       // still awaiting the teacher
    }

    @Test
    void aTestHandedInAfterTheDeadlineStillCountsButIsLate() {
        QuizAttempt done = submitted(9L, due.plus(Duration.ofHours(2)));

        AssignmentProgress progress = AssignmentProgress.of(assignment, List.of(done));

        assertThat(progress.state()).isEqualTo(State.SUBMITTED);
        assertThat(progress.late()).isTrue();
    }

    @Test
    void handingInExactlyAtTheDeadlineIsNotLate() {
        assertThat(AssignmentProgress.of(assignment, List.of(submitted(10L, due))).late()).isFalse();
    }

    @Test
    void aGradedAttemptCarriesItsScore() {
        QuizAttempt done = graded(11L, due.minus(Duration.ofHours(1)), 13);

        AssignmentProgress progress = AssignmentProgress.of(assignment, List.of(done));

        assertThat(progress.state()).isEqualTo(State.GRADED);
        assertThat(progress.score()).isEqualTo(13);
        assertThat(progress.attemptId()).isEqualTo(11L);
    }

    @Test
    void aPracticeNeverCountsAsDoingTheHomework() {
        QuizAttempt practice = attempt(12L, AttemptMode.PRACTICE, created.plusSeconds(90));
        practice.submit();
        practice.completePractice();

        assertThat(AssignmentProgress.of(assignment, List.of(practice)).state()).isEqualTo(State.NOT_STARTED);
    }

    @Test
    void anAttemptFromBeforeTheAssignmentExistedDoesNotCount() {
        QuizAttempt earlier = attempt(13L, AttemptMode.TEST, created.minus(Duration.ofDays(10)));
        earlier.submit();

        assertThat(AssignmentProgress.of(assignment, List.of(earlier)).state()).isEqualTo(State.NOT_STARTED);
    }

    @Test
    void theEarliestHandedInAttemptIsTheOneThatCounts() {
        QuizAttempt first = submitted(14L, due.minus(Duration.ofDays(1)));
        QuizAttempt retake = submitted(15L, due.plus(Duration.ofDays(1)));

        AssignmentProgress progress = AssignmentProgress.of(assignment, List.of(retake, first));

        assertThat(progress.attemptId()).isEqualTo(14L);
        assertThat(progress.late()).isFalse();       // a later retake does not make an on-time submission late
    }

    @Test
    void aHandedInAttemptBeatsAnOpenRetake() {
        QuizAttempt done = submitted(16L, due.minus(Duration.ofHours(3)));
        QuizAttempt retake = attempt(17L, AttemptMode.TEST, created.plus(Duration.ofDays(1)));

        assertThat(AssignmentProgress.of(assignment, List.of(retake, done)).state()).isEqualTo(State.SUBMITTED);
    }

    @Test
    void isDoneOnlyOnceHandedIn() {
        assertThat(AssignmentProgress.of(assignment, List.of()).isDone()).isFalse();
        assertThat(AssignmentProgress.of(assignment, List.of(attempt(1L, AttemptMode.TEST, created.plusSeconds(5)))).isDone()).isFalse();
        assertThat(AssignmentProgress.of(assignment, List.of(submitted(2L, due))).isDone()).isTrue();
    }
}
