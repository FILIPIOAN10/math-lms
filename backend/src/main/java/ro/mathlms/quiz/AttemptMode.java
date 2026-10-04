package ro.mathlms.quiz;

/**
 * How a student sits a quiz. {@code TEST}: timed (if the quiz has a limit), graded, answers withheld until the end.
 * {@code PRACTICE}: no timer, the right answer is shown right after each question, repeatable, and never graded -
 * it does not touch progress, statistics, the grading queue or the result emails.
 */
public enum AttemptMode {
    TEST,
    PRACTICE
}
