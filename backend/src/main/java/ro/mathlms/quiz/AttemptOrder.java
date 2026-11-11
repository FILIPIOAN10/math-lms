package ro.mathlms.quiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

/**
 * The order one attempt shows its questions and options in (V65, Moodle "Shuffle questions"). Seeded by the attempt
 * id, so it is the same every time the student reopens the paper, different from their neighbour's, and needs nothing
 * stored. Questions move only within their section ("SUBIECTUL I" stays first and keeps its questions); grading, the
 * result and the statistics keep the teacher's order - answers are matched by id, never by place.
 */
public final class AttemptOrder {

    private AttemptOrder() {
    }

    /** The questions as this attempt shows them: shuffled within each run of the same section when it says so. */
    public static List<QuizItem> items(QuizAttempt attempt, List<QuizItem> items) {
        if (!attempt.isShuffleItems() || attempt.getId() == null) {
            return items;
        }
        return withinSections(items, QuizItem::getSection, attempt.getId());
    }

    /** One grilă's options as this attempt shows them. */
    public static List<QuizOption> options(QuizAttempt attempt, Long itemId, List<QuizOption> options) {
        if (!attempt.isShuffleOptions() || attempt.getId() == null || options.size() < 2) {
            return options;
        }
        List<QuizOption> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled, new Random(mix(mix(attempt.getId()) ^ itemId)));
        return shuffled;
    }

    /**
     * SplitMix64's finalizer: spreads neighbouring ids (attempt 19, 20...) far apart. {@link Random} seeded with close
     * values draws nearly the same first numbers, so without it two classmates got the same option order.
     */
    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /** {@code items} (in the teacher's order) shuffled inside each consecutive run of one section. */
    static <T> List<T> withinSections(List<T> items, Function<T, String> sectionOf, long seed) {
        List<T> result = new ArrayList<>(items.size());
        int start = 0;
        int run = 0;
        while (start < items.size()) {
            String section = sectionOf.apply(items.get(start));
            int end = start + 1;
            while (end < items.size() && Objects.equals(sectionOf.apply(items.get(end)), section)) {
                end++;
            }
            List<T> group = new ArrayList<>(items.subList(start, end));
            Collections.shuffle(group, new Random(mix(mix(seed) ^ run)));
            result.addAll(group);
            start = end;
            run++;
        }
        return result;
    }
}
