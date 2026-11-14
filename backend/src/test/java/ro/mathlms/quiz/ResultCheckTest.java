package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCheckTest {

    @Test
    void aRequirementThatAsksForNoResultHasNoCheck() {
        assertThat(ResultCheck.of(null, "25")).isNull();
        assertThat(ResultCheck.of("  ", "25")).isNull();
        assertThat(ResultCheck.of(null, null)).isNull();
    }

    @Test
    void nothingTypedIsMissingNotWrong() {
        assertThat(ResultCheck.of("25", null)).isEqualTo(ResultCheck.MISSING);
        assertThat(ResultCheck.of("25", "   ")).isEqualTo(ResultCheck.MISSING);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25    | 25",
            "25    | 25,0",
            "0,5   | 1/2",
            "1/2   | 0.5",
            "3/2   | 1 1/2",
            "1/3   | 0,(3)",
            "-1    | −1",
            "12    |  12 ",
    })
    void theSameValueIsRightHoweverItIsWritten(String expected, String typed) {
        assertThat(ResultCheck.of(expected, typed)).isEqualTo(ResultCheck.RIGHT);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25  | 24",
            "25  | -25",
            "0,5 | 0,05",
            "25  | 25 cm",     // units are not a number: the teacher reads it on the photo
            "25  | x=25",
            "25  | douăzeci",
    })
    void anotherValueOrSomethingThatIsNotANumberIsWrong(String expected, String typed) {
        assertThat(ResultCheck.of(expected, typed)).isEqualTo(ResultCheck.WRONG);
    }
}
