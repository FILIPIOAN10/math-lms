package ro.mathlms.studyplan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** „În urmă": below the straight line from the day the target was set to the target on the exam day. */
class GoalPaceTest {

    private static final LocalDate SET_ON = LocalDate.of(2026, 12, 1);
    private static final LocalDate EXAM = SET_ON.plusDays(100);

    @Test
    void halfwayToTheExamTheyShouldBeHalfwayToTheTarget() {
        LocalDate today = SET_ON.plusDays(50);

        assertThat(GoalPace.expectedToday(SET_ON, 6.0, EXAM, 9.0, today)).isEqualTo(7.5);
        assertThat(GoalPace.behind(SET_ON, 6.0, EXAM, 9.0, 7.1, today)).isTrue();  // more than 0,3 under the line
        assertThat(GoalPace.behind(SET_ON, 6.0, EXAM, 9.0, 7.3, today)).isFalse(); // within 0,3: the estimate wobbles
    }

    @Test
    void nobodyIsBehindInTheFirstTwoWeeks() {
        assertThat(GoalPace.behind(SET_ON, 6.0, EXAM, 9.0, 3.0, SET_ON.plusDays(13))).isFalse();
        assertThat(GoalPace.behind(SET_ON, 6.0, EXAM, 9.0, 3.0, SET_ON.plusDays(14))).isTrue();
    }

    @Test
    void aStudentAlreadyAtTheTargetShouldStayThere() {
        LocalDate today = SET_ON.plusDays(30);

        assertThat(GoalPace.expectedToday(SET_ON, 9.2, EXAM, 9.0, today)).isEqualTo(9.0);
        assertThat(GoalPace.behind(SET_ON, 9.2, EXAM, 9.0, 8.6, today)).isTrue();
        assertThat(GoalPace.behind(SET_ON, 9.2, EXAM, 9.0, 8.8, today)).isFalse();
    }

    @Test
    void afterTheExamDayTheLineStaysAtTheTarget() {
        assertThat(GoalPace.expectedToday(SET_ON, 6.0, EXAM, 9.0, EXAM.plusDays(10))).isEqualTo(9.0);
    }

    @Test
    void withoutAStartingEstimateOrAnExamDateThereIsNoLine() {
        LocalDate today = SET_ON.plusDays(50);

        assertThat(GoalPace.expectedToday(SET_ON, null, EXAM, 9.0, today)).isNull();
        assertThat(GoalPace.expectedToday(SET_ON, 6.0, null, 9.0, today)).isNull();
        assertThat(GoalPace.expectedToday(SET_ON, 6.0, SET_ON, 9.0, today)).isNull(); // set on the exam day itself
        assertThat(GoalPace.behind(SET_ON, null, EXAM, 9.0, 4.0, today)).isFalse();
        assertThat(GoalPace.behind(SET_ON, 6.0, EXAM, 9.0, null, today)).isFalse(); // no estimate today
        assertThat(GoalPace.behind(SET_ON, 6.0, EXAM, null, 4.0, today)).isFalse(); // no target
    }
}
