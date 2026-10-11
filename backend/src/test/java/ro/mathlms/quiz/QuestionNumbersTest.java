package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The journal names a question the way the teacher sees it numbered (same rule as the frontend's numberItems). */
class QuestionNumbersTest {

    private final Quiz quiz = new Quiz("EN", null);

    private QuizItem item(long id, String section) {
        QuizItem item = new QuizItem(quiz, (int) id, QuizItemType.OPEN, "s", 5, null);
        ReflectionTestUtils.setField(item, "id", id);
        item.placeInSection(section);
        return item;
    }

    @Test
    void aTestWithoutHeadingsCountsOneTwoThree() {
        assertThat(QuestionNumbers.of(List.of(item(1, null), item(2, null), item(3, null))))
                .isEqualTo(Map.of(1L, "1", 2L, "2", 3L, "3"));
    }

    @Test
    void underEnHeadingsTheNumberStartsAgainAndCarriesTheRomanNumeral() {
        String first = "SUBIECTUL I\nÎncercuiește litera corectă. (30 de puncte)";
        Map<Long, String> numbers = QuestionNumbers.of(List.of(
                item(1, first), item(2, first), item(3, "SUBIECTUL al II-lea"), item(4, "Subiectul al III-lea")));

        assertThat(numbers).isEqualTo(Map.of(1L, "I.1", 2L, "I.2", 3L, "II.1", 4L, "III.1"));
    }

    @Test
    void aHeadingWithoutARomanNumeralIsNumberedByItsPlace() {
        assertThat(QuestionNumbers.of(List.of(item(1, "Partea întâi"), item(2, "Partea a doua"))))
                .isEqualTo(Map.of(1L, "1.1", 2L, "2.1"));
    }

    @Test
    void anItemMissingFromTheListFallsBackToItsPosition() {
        QuizItem lone = item(9, null);
        assertThat(QuestionNumbers.of(lone, List.of(item(1, null)))).isEqualTo("10");
    }
}
