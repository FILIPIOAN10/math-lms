package ro.mathlms.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtraTimeTest {

    @Test
    void addsTheShareToTheTestsOwnLimitRoundingUp() {
        assertThat(ExtraTime.minutesFor(120, 0)).isEqualTo(120);
        assertThat(ExtraTime.minutesFor(120, 50)).isEqualTo(180);
        assertThat(ExtraTime.minutesFor(120, 100)).isEqualTo(240);
        assertThat(ExtraTime.minutesFor(10, 25)).isEqualTo(13); // 12,5 -> 13: never less than promised
    }

    @Test
    void anUntimedTestStaysUntimed() {
        assertThat(ExtraTime.minutesFor(null, 50)).isNull();
    }

    @Test
    void aStudentStartsWithNoneAndTakesOnlyTheAllowedSteps() {
        User ana = new User("ana@scoala.ro", "Ana", Role.STUDENT);
        assertThat(ana.getExtraTimePercent()).isZero();

        ana.changeExtraTime(50);
        assertThat(ana.getExtraTimePercent()).isEqualTo(50);

        assertThatThrownBy(() -> ana.changeExtraTime(30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ana.changeExtraTime(-25)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAStudentHasExtraTime() {
        User prof = new User("prof@scoala.ro", "Prof", Role.TEACHER);
        assertThatThrownBy(() -> prof.changeExtraTime(50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void erasingTheAccountForgetsIt() {
        User ana = new User("ana@scoala.ro", "Ana", Role.STUDENT);
        ana.changeExtraTime(100);
        ana.erase("sters-1@mathlms.invalid", "Elev șters");
        assertThat(ana.getExtraTimePercent()).isZero();
    }
}
