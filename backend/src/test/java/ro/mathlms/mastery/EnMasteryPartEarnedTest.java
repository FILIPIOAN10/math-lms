package ro.mathlms.mastery;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** When a requirement a), b) of a problem counts on the EN map: only once the problem (or the paper) is graded. */
class EnMasteryPartEarnedTest {

    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final User student = new User("elev@scoala.ro", "Elev Pop", Role.STUDENT);
    private final QuizItem problem = withId(new QuizItem(quiz, 0, QuizItemType.OPEN, "Problema", 10, null), 100L);
    private final QuizItemPart a = withId(new QuizItemPart(problem, 0, "a)", 5, null), 1L);
    private final QuizItemPart b = withId(new QuizItemPart(problem, 1, "b)", 5, null), 2L);

    @Test
    void aHalfGradedProblemDoesNotCountYet() {
        QuizAttempt attempt = submittedTest();
        ItemResponse response = new ItemResponse(attempt, problem);
        response.gradeParts(List.of(a, b), Map.of(1L, 0), null); // the autosave kept a) only

        assertThat(EnMasteryService.partEarned(attempt, response, a)).isNull();
        assertThat(EnMasteryService.partEarned(attempt, response, b)).isNull();
    }

    @Test
    void aFullyGradedProblemCountsEachRequirementBeforeThePaperIsFinished() {
        QuizAttempt attempt = submittedTest();
        ItemResponse response = new ItemResponse(attempt, problem);
        response.gradeParts(List.of(a, b), Map.of(1L, 0, 2L, 5), null);

        assertThat(EnMasteryService.partEarned(attempt, response, a)).isZero();
        assertThat(EnMasteryService.partEarned(attempt, response, b)).isEqualTo(5);
    }

    @Test
    void onAGradedPaperARequirementWithoutPointsIsAZero() {
        QuizAttempt attempt = submittedTest();
        ItemResponse response = new ItemResponse(attempt, problem);
        Map<Long, Integer> onlyA = new HashMap<>();
        onlyA.put(1L, 3);
        response.gradeParts(List.of(a, b), onlyA, null);
        attempt.markGraded(3);

        assertThat(EnMasteryService.partEarned(attempt, response, a)).isEqualTo(3);
        assertThat(EnMasteryService.partEarned(attempt, response, b)).isZero();
        assertThat(EnMasteryService.partEarned(attempt, null, b)).isZero(); // never answered
    }

    @Test
    void aSubmittedPaperWithoutAnAnswerWaitsForTheTeacher() {
        assertThat(EnMasteryService.partEarned(submittedTest(), null, a)).isNull();
    }

    @Test
    void practiceNeverScoresRequirements() {
        QuizAttempt practice = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.PRACTICE);
        practice.submit();
        practice.completePractice();

        assertThat(EnMasteryService.partEarned(practice, null, a)).isNull();
    }

    private QuizAttempt submittedTest() {
        QuizAttempt attempt = new QuizAttempt(quiz, student, Instant.now(), AttemptMode.TEST);
        attempt.submit();
        return attempt;
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
