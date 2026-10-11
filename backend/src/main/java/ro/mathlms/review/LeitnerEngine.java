package ro.mathlms.review;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Leitner rule of E4, with no database in sight. Five boxes, reviewed after 1, 3, 7, 14 and 30 days. A right answer
 * on a card that is due moves it up a box; a wrong answer always sends it back to box 1 (tomorrow); a right answer
 * before the card is due changes nothing - so repeating a practice cannot jump boxes. A first answer starts the card in
 * box 2 when right, box 1 when wrong. Days are the school's days ({@link ReviewService#ZONE}), not UTC.
 */
public final class LeitnerEngine {

    private LeitnerEngine() {
    }

    /** Days until the next review, per box (box 1 = index 0). */
    public static final List<Integer> INTERVAL_DAYS = List.of(1, 3, 7, 14, 30);
    public static final int LAST_BOX = INTERVAL_DAYS.size();

    /** One question's state for one student. */
    public record Card(int box, LocalDate dueOn) {

        public boolean isDue(LocalDate today) {
            return !dueOn.isAfter(today);
        }
    }

    /** One multiple-choice answer: from a practice, a handed-in test or a review. */
    public record Event(Long itemId, boolean correct, Instant at) {
    }

    /** The card after one more answer on {@code day}; {@code current == null} = the question's first answer. */
    public static Card next(Card current, boolean correct, LocalDate day) {
        if (current == null) {
            return schedule(correct ? 2 : 1, day);
        }
        if (!correct) {
            return schedule(1, day);
        }
        if (!current.isDue(day)) {
            return current;
        }
        return schedule(Math.min(LAST_BOX, current.box() + 1), day);
    }

    private static Card schedule(int box, LocalDate day) {
        return new Card(box, day.plusDays(INTERVAL_DAYS.get(box - 1)));
    }

    /** Each question's current card, from its answers replayed in time order. */
    public static Map<Long, Card> replay(List<Event> events, ZoneId zone) {
        Map<Long, Card> cards = new HashMap<>();
        events.stream()
                .sorted(Comparator.comparing(Event::at))
                .forEach(e -> cards.put(e.itemId(),
                        next(cards.get(e.itemId()), e.correct(), e.at().atZone(zone).toLocalDate())));
        return cards;
    }
}
