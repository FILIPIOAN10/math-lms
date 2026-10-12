package ro.mathlms.ingest;

import java.util.List;

/**
 * Reads the questions printed on photographed textbook pages. The seam between the import flow and an AI provider:
 * {@link FakePageExtractor} for dev and tests, a real provider (OpenAI) next to it. It is called outside any
 * transaction, may take tens of seconds, and never sees anything but the page images.
 */
public interface PageExtractor {

    /** The name {@code app.ai.provider} selects it by ("fake", "openai"). */
    String provider();

    /** The questions on {@code pages}, in reading order. Statements only: no answer is ever asked for. */
    ExtractionResult extract(List<PageImage> pages) throws ExtractionException;

    /** One photographed page, as stored. */
    record PageImage(byte[] bytes, String mediaType) {
    }

    /** What the provider read, and what it cost (tokens, for the teacher's monthly bill). */
    record ExtractionResult(List<ExtractedItem> items, String model, Integer inputTokens, Integer outputTokens) {
    }
}
