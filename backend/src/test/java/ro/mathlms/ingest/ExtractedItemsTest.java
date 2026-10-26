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
    void aProblemsRequirementsKeepTheirPointsAndGiveTheTotal() {
        ExtractedItem problem = new ExtractedItem(2, "SUBIECTUL al III-lea", QuizItemType.OPEN, "1. Se consideră $x$.",
                List.of(), null, false, List.of(), List.of(
                        new ExtractedItem.Part("(2p) a) Arată că $x=25$.", 2),
                        new ExtractedItem.Part("b) Calculează $(8y-x)^{27}$.", 3),
                        new ExtractedItem.Part("  ", 1)));

        ExtractedItem out = ExtractedItems.normalize(List.of(problem), 4).getFirst();

        assertThat(out.statement()).isEqualTo("Se consideră $x$.");
        assertThat(out.parts()).containsExactly(new ExtractedItem.Part("Arată că $x=25$.", 2),
                new ExtractedItem.Part("Calculează $(8y-x)^{27}$.", 3));
        assertThat(out.points()).isEqualTo(5);
    }

    private static ExtractedItem on(int page, String section, String statement) {
        return new ExtractedItem(page, section, QuizItemType.SINGLE_CHOICE, statement, List.of("a", "b"), 5, false, List.of());
    }

    @Test
    void pagesSentOutOfOrderStillGiveSubiectulIThenIIThenIII() {
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                // photo 1: the end of SUBIECTUL I (no heading on this page), then SUBIECTUL al II-lea
                on(1, null, "I.5"), on(1, null, "I.6"),
                on(1, "SUBIECTUL al II-lea. Încercuiește litera", "II.1"), on(1, null, "II.2"),
                // photo 2: SUBIECTUL al III-lea
                on(2, "SUBIECTUL al III-lea", "III.1"),
                // photo 3: the start of the test
                on(3, "SUBIECTUL I. Încercuiește litera", "I.1"), on(3, "SUBIECTUL I. Încercuiește litera", "I.2")), 3);

        assertThat(out).extracting(ExtractedItem::statement)
                .containsExactly("I.1", "I.2", "I.5", "I.6", "II.1", "II.2", "III.1");
        // a question under no heading belongs to the one before it, so the numbering runs on (I.5, not 1)
        assertThat(out.get(2).section()).isEqualTo("SUBIECTUL I. Încercuiește litera");
        assertThat(out.get(5).section()).isEqualTo("SUBIECTUL al II-lea. Încercuiește litera");
        assertThat(out.get(2).page()).isEqualTo(1); // the page each came from is kept (previews, figures)
    }

    @Test
    void aTestWithoutSubiectulHeadingsKeepsTheReadingOrder() {
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                on(2, "Exerciții de consolidare", "b"), on(1, null, "a")), 2);

        assertThat(out).extracting(ExtractedItem::statement).containsExactly("b", "a");
        assertThat(out.get(1).section()).isNull();
    }

    @Test
    void enHeadingsReadAsPrinted() {
        assertThat(ExtractedItems.enHeading("SUBIECTUL II-lea\nÎncercuiește litera"))
                .isEqualTo("SUBIECTUL al II-lea\nÎncercuiește litera");
        assertThat(ExtractedItems.enHeading("SUBIECTUL AL III-LEA")).isEqualTo("SUBIECTUL al III-lea");
        assertThat(ExtractedItems.enHeading("SUBIECTUL I")).isEqualTo("SUBIECTUL I");
        assertThat(ExtractedItems.enHeading("Exerciții de consolidare")).isEqualTo("Exerciții de consolidare");
    }

    @Test
    void anEscapedDollarIsNotAFormula() {
        assertThat(ExtractedItems.balancedMath("Costă 5 \\$ și $x=2$")).isTrue();
        assertThat(ExtractedItems.balancedMath("$$x^2$$")).isTrue();
        assertThat(ExtractedItems.balancedMath("$x")).isFalse();
    }

    /** A question with a drawing, the AI's box for it on {@code figurePage}, nothing else. */
    private static ExtractedItem withFigure(int page, Integer figurePage, List<Integer> box) {
        return new ExtractedItem(page, "SUBIECTUL al II-lea", QuizItemType.SINGLE_CHOICE, "În figura alăturată, aria este:",
                List.of("4", "6"), 5, true, List.of(), List.of(), null, null, figurePage, box);
    }

    @Test
    void aFigureBoxIsKeptWithASmallMarginAndTheWarningSaysItWasCut() {
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                withFigure(1, 1, List.of(291, 630, 347, 959)),
                withFigure(2, null, List.of(0, 900, 400, 1000))), 2); // no page given: the question's own

        assertThat(out.get(0).figurePage()).isEqualTo(1);
        assertThat(out.get(0).figureBox()).containsExactly(283, 622, 355, 967);
        assertThat(out.get(0).warnings()).containsExactly(ExtractedItems.FIGURE_CROPPED);
        assertThat(out.get(1).figurePage()).isEqualTo(2);
        assertThat(out.get(1).figureBox()).containsExactly(0, 892, 408, 1000); // the margin stops at the page edge
    }

    @Test
    void aBoxThatCannotBeTrustedIsDroppedAndTheTeacherAddsTheDrawing() {
        List<ExtractedItem> out = ExtractedItems.normalize(List.of(
                withFigure(1, 1, List.of(10, 20, 30)),              // not four numbers
                withFigure(1, 1, List.of(500, 100, 400, 300)),      // upside down
                withFigure(1, 1, List.of(100, 100, 1200, 300)),     // off the page
                withFigure(1, 1, List.of(100, 100, 108, 300)),      // a sliver, not a drawing
                withFigure(1, 3, List.of(100, 100, 300, 300)),      // a page that was not sent
                withFigure(1, 1, null)), 2);

        assertThat(out).allSatisfy(item -> {
            assertThat(item.figurePage()).isNull();
            assertThat(item.figureBox()).isEmpty();
            assertThat(item.warnings()).containsExactly(ExtractedItems.FIGURE);
        });
    }

    @Test
    void noDrawingMeansNoBox() {
        ExtractedItem plain = new ExtractedItem(1, null, QuizItemType.OPEN, "Rezolvă ecuația.", List.of(), 5, false,
                List.of(), List.of(), null, null, 1, List.of(100, 100, 300, 300));

        ExtractedItem out = ExtractedItems.normalize(List.of(plain), 1).getFirst();

        assertThat(out.figurePage()).isNull();
        assertThat(out.figureBox()).isEmpty();
        assertThat(out.warnings()).isEmpty();
    }
}
