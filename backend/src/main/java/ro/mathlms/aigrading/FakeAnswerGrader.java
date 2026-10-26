package ro.mathlms.aigrading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ro.mathlms.ingest.ExtractionException;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in grader for dev and tests: no network, no cost. It proposes full points minus one on the last requirement
 * (or on the whole problem), with a made-up reason, so the grading screen can be clicked through and asserted on.
 * {@code app.ai.fake-delay-ms} makes it feel slow.
 */
@Component
public class FakeAnswerGrader implements AnswerGrader {

    static final String NAME = "fake";

    private final long delayMs;

    public FakeAnswerGrader(@Value("${app.ai.fake-delay-ms:0}") long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public String provider() {
        return NAME;
    }

    @Override
    public Proposal grade(Task task) throws ExtractionException {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExtractionException("Propunerea a fost întreruptă.", e);
            }
        }
        if (task.parts().isEmpty()) {
            return new Proposal(Math.max(0, task.points() - 1), List.of(), "Rezolvare aproape completă (propunere de probă).",
                    AiConfidence.MEDIUM, "fake-grader", null, null);
        }
        List<PartProposal> parts = new ArrayList<>();
        for (int i = 0; i < task.parts().size(); i++) {
            TaskPart part = task.parts().get(i);
            boolean last = i == task.parts().size() - 1;
            parts.add(new PartProposal(part.label(), last ? Math.max(0, part.points() - 1) : part.points(),
                    last ? "Lipsește verificarea finală (probă)." : "Toți pașii din barem (probă)."));
        }
        return new Proposal(parts.stream().mapToInt(PartProposal::points).sum(), parts,
                "Aproape tot corect (propunere de probă).", AiConfidence.MEDIUM, "fake-grader", null, null);
    }
}
