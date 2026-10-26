package ro.mathlms.quiz;

import java.util.List;
import java.util.Map;

/**
 * Told when a TEST attempt is handed in, inside the hand-in transaction - so what it writes commits or rolls back with
 * the hand-in (the AI grading queues its proposals here). Not told about practice.
 */
public interface SubmissionListener {

    /**
     * @param items           the quiz's questions in order
     * @param responsesByItem the student's answers by question id (a question left blank has none)
     */
    void submitted(QuizAttempt attempt, List<QuizItem> items, Map<Long, ItemResponse> responsesByItem);
}
