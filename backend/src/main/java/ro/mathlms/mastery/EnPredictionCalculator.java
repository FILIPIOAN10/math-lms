package ro.mathlms.mastery;

import ro.mathlms.mastery.EnMasteryService.EnChapterMasteryDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The arithmetic behind {@link EnPrediction}, kept free of Spring so it is tested on its own.
 *
 * <p>Points per chapter come from the default places on the paper ({@code en_chapter_slots}): each question is worth
 * 5 points; a Subiectul III problem with requirements of its own ("III.5.a", "III.5.b") splits its 5 points between
 * them, and its own slot ("III.5") then counts nothing. With the V34 links that adds up to 90 points.
 *
 * <p>Estimate = 1 + 9 × expected points ÷ the paper's points (90 with the V34 links), where a chapter's expected
 * points are its paper points × the student's level there (a chapter never met counts at {@link #UNSEEN}). Range
 * ±{@link #WIDE} under {@link #MANY_ANSWERS} answers, ±{@link #NARROW} from then on, within 1 - 10. Under
 * {@link #MIN_ANSWERS} answers there is no estimate.
 */
public final class EnPredictionCalculator {

    static final double POINTS_PER_QUESTION = 5.0;
    static final double PAPER_POINTS = 90.0;
    static final double UNSEEN = 0.3;
    static final int MIN_ANSWERS = 20;
    static final int MANY_ANSWERS = 50;
    static final double WIDE = 0.5;
    static final double NARROW = 0.3;
    static final int TOP_GAINS = 3;

    private EnPredictionCalculator() {
    }

    /** Paper points per chapter code, from the slots ("I.1" → "A1", "III.5.b" → "G7"). */
    public static Map<String, Double> examPoints(Map<String, String> slotToCode) {
        Set<String> problemsWithParts = new HashSet<>();
        Map<String, Integer> partsPerProblem = new HashMap<>();
        for (String slot : slotToCode.keySet()) {
            String[] bits = slot.split("\\.");
            if (bits.length >= 3) {
                String problem = bits[0] + "." + bits[1];
                problemsWithParts.add(problem);
                partsPerProblem.merge(problem, 1, Integer::sum);
            }
        }
        Map<String, Double> points = new HashMap<>();
        slotToCode.forEach((slot, code) -> {
            String[] bits = slot.split("\\.");
            double worth;
            if (bits.length >= 3) {
                worth = POINTS_PER_QUESTION / partsPerProblem.get(bits[0] + "." + bits[1]);
            } else if (problemsWithParts.contains(slot)) {
                worth = 0;
            } else {
                worth = POINTS_PER_QUESTION;
            }
            points.merge(code, worth, Double::sum);
        });
        return points;
    }

    public static EnPrediction predict(Collection<EnChapterMasteryDto> chapters, Map<String, Double> examPoints) {
        int answers = chapters.stream().mapToInt(EnChapterMasteryDto::questions).sum();
        if (answers < MIN_ANSWERS) {
            return new EnPrediction(false, answers, MIN_ANSWERS, null, null, null, List.of());
        }
        // The paper as the admin linked it (90 points with the V34 links), not a fixed 90 (audit 2026-11-27 L3).
        double paper = examPoints.values().stream().mapToDouble(Double::doubleValue).sum();
        if (paper <= 0) {
            paper = PAPER_POINTS;
        }
        double expected = 0;
        List<EnPrediction.Gain> gains = new ArrayList<>();
        for (EnChapterMasteryDto c : chapters) {
            double worth = examPoints.getOrDefault(c.code(), 0.0);
            if (worth == 0) {
                continue;
            }
            double level = c.percent() == null ? UNSEEN : c.percent() / 100.0;
            expected += worth * level;
            double gain = round1((1 - level) * worth * 9 / paper);
            if (gain > 0) {
                gains.add(new EnPrediction.Gain(c.chapterId(), c.code(), c.title(), gain));
            }
        }
        double grade = clamp(1 + 9 * expected / paper);
        double spread = answers < MANY_ANSWERS ? WIDE : NARROW;
        gains.sort(Comparator.comparingDouble(EnPrediction.Gain::gradeGain).reversed()
                .thenComparing(EnPrediction.Gain::code));
        return new EnPrediction(true, answers, MIN_ANSWERS, round2(grade), round2(clamp(grade - spread)),
                round2(clamp(grade + spread)), List.copyOf(gains.subList(0, Math.min(TOP_GAINS, gains.size()))));
    }

    private static double clamp(double grade) {
        return Math.max(1, Math.min(10, grade));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
