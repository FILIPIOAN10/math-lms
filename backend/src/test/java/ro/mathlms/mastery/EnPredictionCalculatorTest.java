package ro.mathlms.mastery;

import org.junit.jupiter.api.Test;
import ro.mathlms.enchapter.EnArea;
import ro.mathlms.mastery.EnMasteryService.EnChapterMasteryDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** „Ce notă iei la EN?": the points on the paper and the estimate built from them. */
class EnPredictionCalculatorTest {

    /** The V34 default links. */
    private static final Map<String, String> V34 = new LinkedHashMap<>();

    static {
        String[][] rows = {
            {"I.1", "A1"}, {"I.2", "A2"}, {"I.3", "A4"}, {"I.4", "A3"}, {"I.5", "A6"}, {"I.6", "A8"},
            {"II.1", "G1"}, {"II.2", "G2"}, {"II.3", "G3"}, {"II.4", "G5"}, {"II.5", "G6"}, {"II.6", "G8"},
            {"III.1", "A9"}, {"III.2", "A10"}, {"III.3", "A11"}, {"III.4", "G5"},
            {"III.5", "G7"}, {"III.5.a", "G5"}, {"III.5.b", "G7"},
            {"III.6", "G9"}, {"III.6.a", "G8"}, {"III.6.b", "G9"},
        };
        for (String[] r : rows) {
            V34.put(r[0], r[1]);
        }
    }

    private static final Map<String, Double> POINTS = EnPredictionCalculator.examPoints(V34);

    /** Every chapter of the paper at this level, {@code questions} answers each. */
    private static List<EnChapterMasteryDto> everyChapterAt(Integer percent, int questions) {
        List<EnChapterMasteryDto> rows = new ArrayList<>();
        long id = 1;
        for (String code : POINTS.keySet().stream().sorted().toList()) {
            rows.add(new EnChapterMasteryDto(id++, code, "Capitol " + code, EnArea.values()[0], 0, 0, percent, questions, true));
        }
        return rows;
    }

    @Test
    void thePaperAddsUpTo90Points() {
        assertThat(POINTS.values().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(90.0);
        assertThat(POINTS.get("G5")).isEqualTo(12.5); // II.4 + III.4 + half of III.5
        assertThat(POINTS.get("G7")).isEqualTo(2.5);  // III.5 itself counts nothing, III.5.b half
    }

    @Test
    void underTwentyAnswersThereIsNoEstimate() {
        EnPrediction p = EnPredictionCalculator.predict(everyChapterAt(80, 1), POINTS); // 17 chapters = 17 answers
        assertThat(p.available()).isFalse();
        assertThat(p.answers()).isEqualTo(17);
        assertThat(p.grade()).isNull();
    }

    @Test
    void everythingMasteredIsATen() {
        EnPrediction p = EnPredictionCalculator.predict(everyChapterAt(100, 4), POINTS);
        assertThat(p.grade()).isEqualTo(10.0);
        assertThat(p.high()).isEqualTo(10.0);
        assertThat(p.topGains()).isEmpty();
    }

    @Test
    void chaptersNeverMetCountAtThirtyPercent() {
        List<EnChapterMasteryDto> rows = everyChapterAt(null, 0);
        rows.add(new EnChapterMasteryDto(99L, "X1", "Alt capitol", EnArea.values()[0], 0, 0, 50, 25, true));
        EnPrediction p = EnPredictionCalculator.predict(rows, POINTS);
        assertThat(p.grade()).isEqualTo(3.7); // 1 + 9 × 27 / 90
        assertThat(p.low()).isEqualTo(3.2);   // 25 answers: ±0.5
        assertThat(p.high()).isEqualTo(4.2);
    }

    @Test
    void fiftyAnswersNarrowTheRange() {
        EnPrediction p = EnPredictionCalculator.predict(everyChapterAt(60, 3), POINTS); // 51 answers
        assertThat(p.grade()).isEqualTo(6.4); // 1 + 9 × 54 / 90
        assertThat(p.low()).isEqualTo(6.1);
        assertThat(p.high()).isEqualTo(6.7);
    }

    @Test
    void theBiggestGainComesFirst() {
        EnPrediction p = EnPredictionCalculator.predict(everyChapterAt(60, 3), POINTS);
        assertThat(p.topGains()).hasSize(3);
        assertThat(p.topGains().get(0).code()).isEqualTo("G5"); // 12.5 points × 40% missing × 0.1 = 0.5
        assertThat(p.topGains().get(0).gradeGain()).isEqualTo(0.5);
    }
}
