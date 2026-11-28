package ro.mathlms.studyplan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * „În urmă": where a student should be today on the way to their target - the straight line from their estimate on the
 * day they set the target to the target on the exam day (or the target itself, for one who was already there) - and
 * whether they are more than {@value #TOLERANCE} under it. Nobody is behind in the first {@value #GRACE_DAYS} days:
 * the estimate moves with every answer, two weeks say more than two days.
 */
final class GoalPace {

    static final int GRACE_DAYS = 14;
    /** The estimate's own range with 50+ answers: less than that under the line is noise. */
    static final double TOLERANCE = 0.3;

    private GoalPace() {
    }

    /** Null when no line can be drawn: no starting estimate, no exam date, or the target set on the exam day. */
    static Double expectedToday(LocalDate startDay, Double startGrade, LocalDate examDate, Double target, LocalDate today) {
        if (startDay == null || startGrade == null || examDate == null || target == null) {
            return null;
        }
        long total = ChronoUnit.DAYS.between(startDay, examDate);
        if (total <= 0) {
            return null;
        }
        if (startGrade >= target) {
            return target;
        }
        long elapsed = Math.clamp(ChronoUnit.DAYS.between(startDay, today), 0, total);
        return Math.round((startGrade + (target - startGrade) * elapsed / total) * 100) / 100.0;
    }

    static boolean behind(LocalDate startDay, Double startGrade, LocalDate examDate, Double target, Double grade,
                          LocalDate today) {
        Double expected = expectedToday(startDay, startGrade, examDate, target, today);
        return expected != null && grade != null
                && ChronoUnit.DAYS.between(startDay, today) >= GRACE_DAYS
                && grade < expected - TOLERANCE - 1e-9;
    }
}
