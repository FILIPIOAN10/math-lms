package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NumericAnswerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0,5 | 1/2",
            "0.5 | 1/2",
            ",5 | 1/2",
            "2/4 | 1/2",
            "1:2 | 1/2",
            "12 | 12,0",
            "12 | 24/2",
            "-3/4 | -0,75",
            "−3/4 | -0,75",
            "+7 | 7",
            "1 1/2 | 1,5",
            "1 1/2 | 3/2",
            "-1 1/2 | -3/2",
            "0,(3) | 1/3",
            "1,2(3) | 37/30",
            "0,(9) | 1",
            "  3  /  4  | 0,75",
    })
    void equivalentFormsHaveTheSameValue(String one, String other) {
        assertThat(NumericAnswer.same(one, other)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"0,5 | 0,51", "1/3 | 0,33", "1/2 | -1/2", "2 | 3", "1 1/2 | 11/2"})
    void differentValuesAreDifferent(String one, String other) {
        assertThat(NumericAnswer.same(one, other)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "20 cm", "x=3", "2√3", "1/0", "1/", "/2", "1,2,3", "1 2", "0,(", "3-"})
    void whatIsNotANumberIsRefused(String text) {
        assertThat(NumericAnswer.parse(text)).isEmpty();
    }

    @Test
    void nothingCanStandForANumber() {
        assertThat(NumericAnswer.parse(null)).isEmpty();
        assertThat(NumericAnswer.same(null, "1")).isFalse();
        assertThat(NumericAnswer.same("1", null)).isFalse();
    }

    @Test
    void anAbsurdlyLongTextIsNotCounted() {
        assertThat(NumericAnswer.parse("1".repeat(NumericAnswer.MAX_LENGTH + 1))).isEmpty();
    }

    @Test
    void equalValuesShareOneKey() {
        assertThat(NumericAnswer.parse("0,5").orElseThrow().key()).isEqualTo(NumericAnswer.parse("2/4").orElseThrow().key());
        assertThat(NumericAnswer.parse("-3").orElseThrow().key()).isEqualTo("-3/1");
    }
}
