package ro.mathlms.ingest;

import org.junit.jupiter.api.Test;
import ro.mathlms.quiz.QuizItemType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractedItemsTest {

    private static ExtractedItem item(QuizItemType type, String statement, List<String> options) {
        return new ExtractedItem(1, null, type, statement, options, 5, false, List.of());
    }

    @Test
    void trimsAndDropsEmptyQuestionsAndOptions() {
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                item(QuizItemType.SINGLE_CHOICE, "  Calculează $2+3$  ", Arrays.asList(" 5 ", "", null, "6")),
                item(QuizItemType.OPEN, "   ", List.of())), 1);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().statement()).isEqualTo("Calculează $2+3$");
        assertThat(out.getFirst().options()).containsExactly("5", "6");
        assertThat(out.getFirst().warnings()).isEmpty();
    }

    @Test
    void warnsInsteadOfGuessing() {
        ExtractedItem figure = new ExtractedItem(1, "SUBIECTUL al II-lea", QuizItemType.OPEN, "În figură, $AB = 3",
                List.of(), 10, true, List.of());
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                figure, item(QuizItemType.SINGLE_CHOICE, "Alege", List.of("1"))), 1);

        assertThat(out.get(0).warnings()).containsExactly(ExtractedItems.UNBALANCED_MATH, ExtractedItems.FIGURE);
        assertThat(out.get(1).warnings()).containsExactly(ExtractedItems.TOO_FEW_OPTIONS);
    }

    @Test
    void anOpenQuestionHasNoOptionsAndAMissingTypeIsOpen() {
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                item(QuizItemType.OPEN, "Rezolvă", List.of("a", "b")), item(null, "Arată că", null)), 1);

        assertThat(out).allMatch(i -> i.type() == QuizItemType.OPEN && i.options().isEmpty());
    }

    @Test
    void unlikelyNumbersAreDroppedOrClamped() {
        ExtractedItem odd = new ExtractedItem(9, null, QuizItemType.OPEN, "Rezolvă", List.of(), 1000, false, null);
        ExtractedItem out = ExtractedItems.normalize(List.of(odd), 2).getFirst();

        assertThat(out.points()).isNull();
        assertThat(out.page()).isEqualTo(2);
    }

    @Test
    void capsHowManyQuestionsOnePassKeeps() {
        List<ExtractedItem> many = new ArrayList<>();
        for (int i = 0; i < ExtractedItems.MAX_ITEMS + 10; i++) {
            many.add(item(QuizItemType.OPEN, "Exercițiul " + i, List.of()));
        }
        assertThat(ExtractedItems.normalize(many, 5)).hasSize(ExtractedItems.MAX_ITEMS);
        assertThat(ExtractedItems.normalize(null, 5)).isEmpty();
    }

    @Test
    void theQuizNumbersQuestionsSoPrintedNumbersGo() {
        assertThat(ExtractedItems.withoutNumber("1. Calculează $2+3$")).isEqualTo("Calculează $2+3$");
        assertThat(ExtractedItems.withoutNumber("12) Arată că")).isEqualTo("Arată că");
        assertThat(ExtractedItems.withoutNumber("Ex. 3. Rezolvă")).isEqualTo("Rezolvă");
        assertThat(ExtractedItems.withoutNumber("2.5 este un număr zecimal")).isEqualTo("2.5 este un număr zecimal");
        assertThat(ExtractedItems.withoutNumber("3 + 4 = ?")).isEqualTo("3 + 4 = ?");
        assertThat(ExtractedItems.normalize(List.of(item(QuizItemType.OPEN, "4. Rezolvă", List.of())), 1).getFirst()
                .statement()).isEqualTo("Rezolvă");
    }

    @Test
    void anEscapedDollarIsNotAFormula() {
        assertThat(ExtractedItems.balancedMath("Costă 5 \\$ și $x=2$")).isTrue();
        assertThat(ExtractedItems.balancedMath("$$x^2$$")).isTrue();
        assertThat(ExtractedItems.balancedMath("$x")).isFalse();
    }
}
