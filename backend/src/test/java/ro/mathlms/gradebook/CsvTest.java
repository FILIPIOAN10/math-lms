package ro.mathlms.gradebook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void aPlainCellIsLeftAsItIs() {
        assertThat(Csv.text("Ana Pop")).isEqualTo("Ana Pop");
        assertThat(Csv.text(null)).isEmpty();
    }

    @Test
    void aCellWithASeparatorAQuoteOrALineBreakIsQuoted() {
        assertThat(Csv.text("Test; partea 1")).isEqualTo("\"Test; partea 1\"");
        assertThat(Csv.text("Testul \"mare\"")).isEqualTo("\"Testul \"\"mare\"\"\"");
        assertThat(Csv.text("două\nrânduri")).isEqualTo("\"două\nrânduri\"");
    }

    @Test
    void aCellThatASpreadsheetWouldRunAsAFormulaIsDefused() {
        assertThat(Csv.text("=1+1")).isEqualTo("'=1+1");
        assertThat(Csv.text("+40 721")).isEqualTo("'+40 721");
        assertThat(Csv.text("-1")).isEqualTo("'-1");
        assertThat(Csv.text("@ana")).isEqualTo("'@ana");
        // Defused first, quoted second: the apostrophe must end up inside the quotes.
        assertThat(Csv.text("=HYPERLINK(\"http://rau.example\";\"Ana\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://rau.example\"\";\"\"Ana\"\")\"");
    }

    @Test
    void aLineJoinsItsCellsAndEndsWithCrLf() {
        assertThat(Csv.line(List.of("Elev", "Test 1"))).isEqualTo("Elev;Test 1\r\n");
    }

    @Test
    void gradesUseTheAppFormulaAndADecimalComma() {
        assertThat(ClassGradebookService.grade(5, 10)).isEqualByComparingTo("5.50");
        assertThat(ClassGradebookService.formatGrade(ClassGradebookService.grade(10, 10))).isEqualTo("10");
        assertThat(ClassGradebookService.formatGrade(ClassGradebookService.grade(8, 10))).isEqualTo("8,20");
        assertThat(ClassGradebookService.formatGrade(ClassGradebookService.grade(0, 10))).isEqualTo("1");
    }
}
