package ro.mathlms.review;

/**
 * How sure the student says they are of a daily-review answer, tapped before "Verifică". Optional and never graded:
 * a right answer given as a {@link #GUESS} does not move the card up a box ({@link LeitnerEngine}), and the
 * {@link #SURE} ones feed the student's calibration line.
 */
public enum Confidence {
    GUESS,
    THINK,
    SURE
}
