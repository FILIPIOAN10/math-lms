package ro.mathlms.ingest;

import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.QuizItemType;

import java.util.List;

/**
 * Reads the official barem of a test from photos and maps it onto the test's questions: the right letter of each grilă,
 * the barem of each open question or of each requirement a), b)... One implementation per provider, picked by
 * {@code app.ai.grading.provider}. Only the barem photos and the test's own questions are sent - no student work.
 */
public interface BaremReader {

    /** The {@code app.ai.grading.provider} value that selects this reader. */
    String provider();

    Result read(List<PageImage> pages, List<OutlineItem> questions) throws ExtractionException;

    /** One question of the test as the reader is told about it, numbered as on screen ("I.1", "III.2", "3"). */
    record OutlineItem(String label, QuizItemType type, String statement, List<String> options, int points,
                       List<OutlinePart> parts) {
    }

    /** A requirement a), b)... of an open question. */
    record OutlinePart(String label, String statement, int points) {
    }

    /**
     * The barem of one question: {@code correctLetter} for a grilă ("b"), {@code barem} for an open question without
     * requirements (or general remarks), {@code parts} for its requirements; {@code warnings} in Romanian.
     */
    record Entry(String label, String correctLetter, String barem, List<PartBarem> parts, List<String> warnings) {
    }

    record PartBarem(String label, String barem) {
    }

    record Result(List<Entry> entries, String model, Integer inputTokens, Integer outputTokens) {
    }
}
