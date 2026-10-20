package ro.mathlms.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.quiz.QuizItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in provider for dev and tests: no network, no key, no cost. Every page "contains" the same two questions -
 * a grilă under SUBIECTUL I and an open question with a figure (and its box) under SUBIECTUL al II-lea - so the whole flow (upload,
 * worker, review, quiz) can be clicked through and asserted on. {@code app.ai.fake-delay-ms} makes it feel slow.
 */
@Component
public class FakePageExtractor implements PageExtractor {

    static final String NAME = "fake";

    private final long delayMs;

    public FakePageExtractor(@Value("${app.ai.fake-delay-ms:0}") long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public ExtractionResult extract(List<PageImage> pages) throws ExtractionException {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtractionException("Citirea a fost întreruptă.", e);
            }
        }
        List<ExtractedItem> items = new ArrayList<>();
        for (int page = 1; page <= pages.size(); page++) {
            items.add(new ExtractedItem(page, "SUBIECTUL I", QuizItemType.SINGLE_CHOICE,
                    "Rezultatul calculului $" + page + " + 2 \\cdot 3$ este:",
                    List.of(String.valueOf(page + 4), String.valueOf(page + 6), String.valueOf(3 * page + 6)),
                    5, false, List.of()));
            items.add(new ExtractedItem(page, "SUBIECTUL al II-lea", QuizItemType.OPEN,
                    "În figura alăturată, triunghiul $ABC$ are $AB = " + (page + 5) + "$ cm. Calculează perimetrul triunghiului.",
                    List.of(), 10, true, List.of(), List.of(), null, null,
                    page, List.of(200, 300, 500, 500))); // the drawing: a box on its page, cut when the quiz is made
        }
        return new ExtractionResult(items, "fake-1", 0, 0);
    }
}
