package ro.mathlms.aiusage;

/**
 * Notes one billed AI call: which feature asked, which model answered, and the tokens it reported (output includes the
 * thinking). Called by the provider clients as soon as an answer arrives, before it is checked, so a call whose answer
 * is then refused still counts. Never throws: a lost row must not fail the call that was already paid for.
 */
@FunctionalInterface
public interface AiUsageRecorder {

    String IMPORT = "IMPORT";
    String BAREM = "BAREM";
    String GRADING = "GRADING";
    String CHAPTERS = "CHAPTERS";
    String MISTAKES = "MISTAKES";

    /** Records nothing: the default of every client until Spring hands it the real one, and in unit tests. */
    AiUsageRecorder NONE = (feature, model, inputTokens, outputTokens) -> { };

    void record(String feature, String model, Integer inputTokens, Integer outputTokens);
}
