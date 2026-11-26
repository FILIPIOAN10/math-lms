package ro.mathlms.mastery;

import java.util.List;

/**
 * „Ce notă iei la EN?": an estimate of the EN grade from the student's mastery of each EN chapter, weighted by how many
 * points that chapter carries on the paper. {@code available} false = too few answers yet ({@code answers} of
 * {@code answersNeeded}); then {@code low}, {@code high} and {@code grade} are null and {@code topGains} is empty.
 */
public record EnPrediction(boolean available, int answers, int answersNeeded, Double grade, Double low, Double high,
                           List<Gain> topGains) {

    /** A chapter worth practising: how much the estimate would rise if the student mastered it fully. */
    public record Gain(Long chapterId, String code, String title, double gradeGain) {
    }
}
