package ro.mathlms.quiz;

/**
 * What the result a student typed for a requirement says, against the result its barem expects - with no AI and no
 * photo reading: the two are compared by value ({@link NumericAnswer}). It is only a hint for the teacher: a right
 * result proposes the requirement's points, it never gives them (the barem also pays for the method, which is on the
 * photo), and a wrong or missing one proposes nothing.
 */
public enum ResultCheck {
    /** The typed result is the expected value, however it was written. */
    RIGHT,
    /** Something else was typed: another value, or not a number at all. */
    WRONG,
    /** The requirement asks for a result and the student typed none. */
    MISSING;

    /** The check for one requirement, or {@code null} when it asks for no result. */
    public static ResultCheck of(String expected, String typed) {
        if (expected == null || expected.isBlank()) {
            return null;
        }
        if (typed == null || typed.isBlank()) {
            return MISSING;
        }
        return NumericAnswer.same(expected, typed) ? RIGHT : WRONG;
    }
}
