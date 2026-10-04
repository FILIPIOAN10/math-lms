package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NaturalOrderTest {

    private static List<String> sorted(String... names) {
        List<String> list = new ArrayList<>(List.of(names));
        list.sort(NaturalOrder.BY_NAME);
        return list;
    }

    @Test
    void numbersInsideNamesCompareAsNumbersNotAsText() {
        assertThat(sorted("Capitolul 10", "Capitolul 2", "Capitolul 1"))
                .containsExactly("Capitolul 1", "Capitolul 2", "Capitolul 10");
    }

    @Test
    void classNamesWithTheGradeInTheMiddleSortByGrade() {
        assertThat(sorted("Clasa a 10-a", "Clasa a 9-a", "Clasa a 12-a", "Clasa a 5-a"))
                .containsExactly("Clasa a 5-a", "Clasa a 9-a", "Clasa a 10-a", "Clasa a 12-a");
    }

    @Test
    void textIsComparedIgnoringCaseAndRomanianDiacriticsStayInPlace() {
        assertThat(sorted("ecuații", "Algebră", "Funcții")).containsExactly("Algebră", "ecuații", "Funcții");
    }

    @Test
    void leadingZerosAndEqualNumbersFallBackToAStableOrder() {
        assertThat(sorted("Tema 02", "Tema 2", "Tema 1")).containsExactly("Tema 1", "Tema 02", "Tema 2");
    }

    @Test
    void aNameIsBeforeItsOwnLongerVersion() {
        assertThat(sorted("Algebra 2", "Algebra")).containsExactly("Algebra", "Algebra 2");
    }

    @Test
    void hugeNumbersDoNotOverflow() {
        assertThat(sorted("Cap 99999999999999999999999", "Cap 3")).containsExactly("Cap 3", "Cap 99999999999999999999999");
    }

    @Test
    void nullsSortFirstInsteadOfThrowing() {
        List<String> list = new ArrayList<>();
        list.add("b");
        list.add(null);
        list.sort(NaturalOrder.BY_NAME);

        assertThat(list).containsExactly(null, "b");
    }
}
