package ro.mathlms.user;

import java.util.List;

/**
 * Extra time on a timed test for one student (CES, WCAG 2.2.1 "Timing Adjustable"): a share of the test's own limit,
 * added when the attempt starts. The teacher picks one of {@link #ALLOWED}; the database refuses any other value too.
 */
public final class ExtraTime {

    /** None, a quarter, half, double the time. */
    public static final List<Integer> ALLOWED = List.of(0, 25, 50, 100);

    private ExtraTime() {
    }

    /** The minutes this student gets on a test of {@code limitMinutes}, rounded up; null (untimed) stays null. */
    public static Integer minutesFor(Integer limitMinutes, int percent) {
        if (limitMinutes == null) return null;
        return (limitMinutes * (100 + percent) + 99) / 100;
    }

    public static boolean isAllowed(int percent) {
        return ALLOWED.contains(percent);
    }
}
