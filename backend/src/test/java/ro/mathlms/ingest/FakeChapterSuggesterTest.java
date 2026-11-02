package ro.mathlms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ro.mathlms.ingest.ChapterSuggester.Choice;
import ro.mathlms.ingest.ChapterSuggester.Question;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeChapterSuggesterTest {

    private static final List<Choice> CHAPTERS = EnChapterClues.BY_CODE.keySet().stream()
            .map(code -> new Choice(code, code, EnChapterClues.BY_CODE.get(code))).toList();

    private static String suggest(String statement) {
        Question question = new Question("SUBIECTUL I", statement, List.of(), List.of());
        return new FakeChapterSuggester().suggest(List.of(question), CHAPTERS).getFirst().code();
    }

    /** The chapters of the 2026 programme, each by the words that give it away. */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
            Aduceți fracțiile ordinare la același numitor.; A12
            Aflați numărul de pagini prin metoda mersului invers.; A13
            Rezolvați ecuația de gradul al doilea.; A14
            Rezolvați $x^2 - 5x + 6 = 0$.; A14
            Care este probabilitatea să apară un număr prim?; A15
            Aflați frecvența notei 8.; A15
            Calculați volumul trunchiului de piramidă.; G10
            Calculați aria secțiunii axiale a conului.; G10
            """)
    void recognisesTheProgrammeChapters(String statement, String code) {
        assertThat(suggest(statement)).isEqualTo(code);
    }

    /** A plain cube or equation still lands where it did. */
    @Test
    void keepsTheChaptersItAlreadyKnew() {
        assertThat(suggest("Calculați diagonala cubului.")).isEqualTo("G8");
        assertThat(suggest("Rezolvați ecuația $3x - 5 = 7$.")).isEqualTo("A7");
    }

    @Test
    void everyChapterOfTheProgrammeHasItsClues() {
        assertThat(EnChapterClues.BY_CODE).containsKeys("A12", "A13", "A14", "A15", "G10").hasSize(25);
    }
}
