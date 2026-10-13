package ro.mathlms.ingest;

import java.util.Collections;
import java.util.List;

/**
 * Proposes the EN chapter of each question - and of each requirement a), b)... - from its text alone (never a photo,
 * never anything about a student). The teacher always confirms: a proposal only pre-fills a choice. Selected by the
 * same {@code app.ai.provider} as the page reader: {@link FakeChapterSuggester} for dev and tests, OpenAI otherwise.
 */
public interface ChapterSuggester {

    String provider();

    /**
     * One suggestion per question, in the same order; a code that is not among {@code chapters} comes back as null.
     * Requirements get one suggestion each, in their order.
     */
    List<Suggestion> suggest(List<Question> questions, List<Choice> chapters) throws ExtractionException;

    /** What is known about a question: its heading, statement, choices (grilă) and requirements' texts. */
    record Question(String section, String statement, List<String> options, List<String> parts) {
    }

    /** A chapter it may go to, with the clues that recognise it ("cub, prismă, volum"). */
    record Choice(String code, String title, String clues) {
    }

    /** {@code code} null = no proposal. */
    record Suggestion(String code, String reason, List<PartSuggestion> parts) {

        static Suggestion none(int parts) {
            return new Suggestion(null, null, Collections.nCopies(parts, new PartSuggestion(null, null)));
        }
    }

    record PartSuggestion(String code, String reason) {
    }
}
