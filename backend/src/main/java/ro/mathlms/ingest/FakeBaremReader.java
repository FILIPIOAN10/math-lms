package ro.mathlms.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.QuizItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in barem reader for dev and tests: no network, no cost. Whatever the photos, every grilă's answer is "b"
 * (or "a" with a single option) and every open question gets a two-step sample barem per requirement, so the review
 * and the apply can be clicked through. {@code app.ai.fake-delay-ms} makes it feel slow.
 */
@Component
public class FakeBaremReader implements BaremReader {

    static final String NAME = "fake";

    private final long delayMs;

    public FakeBaremReader(@Value("${app.ai.fake-delay-ms:0}") long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public Result read(List<PageImage> pages, List<OutlineItem> questions) throws ExtractionException {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtractionException("Citirea a fost întreruptă.", e);
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (OutlineItem q : questions) {
            if (q.type() == QuizItemType.SINGLE_CHOICE) {
                entries.add(new Entry(q.label(), q.options().size() > 1 ? "b" : "a", null, List.of(), List.of()));
            } else if (q.parts().isEmpty()) {
                entries.add(new Entry(q.label(), null, sample(q.points()), List.of(), List.of()));
            } else {
                List<PartBarem> parts = q.parts().stream()
                        .map(p -> new PartBarem(p.label(), sample(p.points()))).toList();
                entries.add(new Entry(q.label(), null, null, parts, List.of()));
            }
        }
        return new Result(entries, "fake-barem", null, null);
    }

    /** Two steps that add up to the points: "Pasul 1 ... 1p" / "Pasul 2 ... (points - 1)p". */
    private static String sample(int points) {
        if (points <= 1) {
            return "Rezultatul corect ... " + points + "p";
        }
        return "Scrierea relației corecte ... 1p\nFinalizare: rezultatul corect ... " + (points - 1) + "p";
    }
}
