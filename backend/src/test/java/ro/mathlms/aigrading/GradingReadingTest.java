package ro.mathlms.aigrading;

import org.junit.jupiter.api.Test;
import ro.mathlms.aigrading.AnswerGrader.PartProposal;
import ro.mathlms.aigrading.AnswerGrader.Proposal;
import ro.mathlms.aigrading.AnswerGrader.Task;
import ro.mathlms.aigrading.AnswerGrader.TaskPart;
import ro.mathlms.ingest.ExtractionException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How the AI's answer is read: never more than the barem allows, never a requirement left out. */
class GradingReadingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Task WITH_PARTS = new Task("Un teren.", 5, null,
            List.of(new TaskPart("a", "Perimetrul.", 2, "$P = 130$ ... 2p"), new TaskPart("b", "Aria.", 3, "... 3p")),
            null, List.of());

    private static Proposal read(String json, Task task) throws ExtractionException {
        return GradingReading.proposal(JSON, json, task, "gemini-4-argon", 100, 200);
    }

    @Test
    void keepsEveryRequirementWithinItsPointsAndAddsThemUpItself() throws Exception {
        Proposal proposal = read("""
                {"points": 99, "reason": "Bine.", "confidence": "high", "readable": true,
                 "parts": [{"label": "B)", "points": 7, "reason": "Complet."}, {"label": "a", "points": -1, "reason": " "}]}
                """, WITH_PARTS);

        assertThat(proposal.parts()).containsExactly(new PartProposal("a", 0, null), new PartProposal("b", 3, "Complet."));
        assertThat(proposal.points()).isEqualTo(3); // the model's 99 is not trusted
        assertThat(proposal.confidence()).isEqualTo(AiConfidence.HIGH);
    }

    @Test
    void aRequirementTheAiLeftOutGetsZeroAndSaysSo() throws Exception {
        Proposal proposal = read("""
                {"points": 2, "reason": "", "confidence": "MEDIUM", "readable": true,
                 "parts": [{"label": "a", "points": 2, "reason": "Corect."}]}
                """, WITH_PARTS);

        assertThat(proposal.parts().get(1)).isEqualTo(new PartProposal("b", 0, "AI-ul nu a punctat această cerință."));
        assertThat(proposal.reason()).isNull();
    }

    @Test
    void aProblemWithoutRequirementsIsClampedToItsPoints() throws Exception {
        Task whole = new Task("Rezolvă.", 4, "... 4p", List.of(), null, List.of());

        assertThat(read("""
                {"points": 6, "parts": [], "reason": "Tot.", "confidence": "SURE", "readable": true}
                """, whole)).satisfies(p -> {
            assertThat(p.points()).isEqualTo(4);
            assertThat(p.confidence()).isEqualTo(AiConfidence.LOW); // an unknown confidence is the lowest
        });
    }

    @Test
    void photosWithoutASolutionOrAnAnswerThatIsNotJsonAreReadableFailures() {
        assertThatThrownBy(() -> read("""
                {"points": 0, "parts": [], "reason": "", "confidence": "LOW", "readable": false}
                """, WITH_PARTS)).isInstanceOf(ExtractionException.class).hasMessage(GradingReading.NOT_A_SOLUTION);
        assertThatThrownBy(() -> read("nu e json", WITH_PARTS))
                .isInstanceOf(ExtractionException.class).hasMessage(GradingReading.UNREADABLE_ANSWER);
    }

    @Test
    void writesTheProblemWithEachRequirementsBarem() {
        String text = GradingReading.problem(new Task("Un teren.", 5, "Observație.",
                List.of(new TaskPart("a", "Perimetrul.", 2, "$P = 130$ ... 2p"), new TaskPart("b", "Aria.", 3, null)),
                null, List.of()));

        assertThat(text).contains("Problema (5p):\nUn teren.")
                .contains("Observații din barem:\nObservație.")
                .contains("a) (2p) Perimetrul.\nBarem a):\n$P = 130$ ... 2p")
                .contains("Barem b):\n(fără barem: punctează după corectitudinea rezolvării)");
    }
}
