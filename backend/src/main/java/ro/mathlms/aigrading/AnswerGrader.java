package ro.mathlms.aigrading;

import ro.mathlms.ingest.ExtractionException;
import ro.mathlms.ingest.PageExtractor.PageImage;

import java.util.List;

/**
 * Proposes points for one photographed answer against its barem. One implementation per provider, picked by
 * {@code app.ai.grading.provider}. Only the answer's photos, the statement (and its figure) and the barem are sent -
 * never a name or an account.
 */
public interface AnswerGrader {

    /** The {@code app.ai.grading.provider} value that selects this grader. */
    String provider();

    Proposal grade(Task task) throws ExtractionException;

    /**
     * One answer to grade: the problem as the test has it ({@code barem} of the whole problem, or per requirement in
     * {@code parts}), its figure if it has one, and the pages the student photographed, in order.
     */
    record Task(String statement, int points, String barem, List<TaskPart> parts, PageImage figure,
                List<PageImage> photos) {
    }

    record TaskPart(String label, String statement, int points, String barem) {
    }

    /**
     * What the AI proposes: {@code points} for the whole answer (the sum of the requirements' when there are some),
     * a short reason for the teacher, how sure it is, and per requirement the same. Points are already within bounds.
     */
    record Proposal(int points, List<PartProposal> parts, String reason, AiConfidence confidence, String model,
                    Integer inputTokens, Integer outputTokens) {
    }

    record PartProposal(String label, int points, String reason) {
    }
}
