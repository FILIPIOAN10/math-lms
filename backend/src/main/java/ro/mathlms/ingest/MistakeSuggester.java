package ro.mathlms.ingest;

/**
 * Proposes what mistake leads a student to a wrong option of a grilă, from the question's TEXT alone (never a photo,
 * never anything about a student). The teacher always decides: a proposal only fills a text box. Selected by the same
 * {@code app.ai.provider} as the page reader.
 */
public interface MistakeSuggester {

    String provider();

    /** One or two sentences to the student who chose {@code wrong}; never empty. */
    String suggest(Question question) throws ExtractionException;

    /** What is known about the question: its statement, the right answer (may be null) and the wrong option. */
    record Question(String statement, String correct, String wrong) {
    }
}
