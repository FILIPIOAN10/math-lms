package ro.mathlms.recap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The same reading of "this statement points at a drawing" as tutor/answer_check.mentions_figure. */
class RecapFiguresTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "În figura 1, punctele $A$, $B$ și $C$ sunt coliniare.",
            "Graficul funcțiilor este prezentat în figura alăturată.",
            "Figura 2 reprezintă o hartă.",
            "Fie dreptunghiul $ABCD$ din figura 17, cu $AD=5$.",
            "Triunghiul $ABC$ (fig. 3) are $AB=6$.",
            "Triunghiul $ABC$ din figură are $AB = 3$.",
    })
    void aStatementPointingAtADrawingMentionsAFigure(String statement) {
        assertThat(RecapFigures.mentionsFigure(statement)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Calculați $\\frac{48}{6}$.\n<!-- warning: No figures found. -->",
            "Un număr figurează pe listă de două ori.",
            "Rezolvați prin metoda figurativă.",
            "Desenați un pătrat cu latura de 3 cm.",
    })
    void aTranscriptionNoteOrAnotherWordIsNotAFigure(String statement) {
        assertThat(RecapFigures.mentionsFigure(statement)).isFalse();
    }
}
