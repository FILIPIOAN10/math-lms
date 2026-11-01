package ro.mathlms.quiz;

/**
 * Kind of quiz item (subiect):
 * <ul>
 *   <li>{@code SINGLE_CHOICE} — grilă with options, exactly one correct; graded automatically.</li>
 *   <li>{@code OPEN} — rezolvare completă; the student uploads a photo and a teacher grades it.</li>
 *   <li>{@code NUMERIC} — the student types a number; graded automatically, by value ({@link NumericAnswer}).</li>
 * </ul>
 */
public enum QuizItemType {
    SINGLE_CHOICE,
    OPEN,
    NUMERIC
}
