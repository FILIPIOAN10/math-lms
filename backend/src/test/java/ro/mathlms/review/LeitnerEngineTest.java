package ro.mathlms.review;

import org.junit.jupiter.api.Test;
import ro.mathlms.review.LeitnerEngine.Card;
import ro.mathlms.review.LeitnerEngine.Event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeitnerEngineTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    /** 10:00 local time on the given day. */
    private static Instant on(LocalDate day) {
        return day.atTime(10, 0).atZone(ZONE).toInstant();
    }

    @Test
    void theBoxesAreOneThreeSevenFourteenAndThirtyDays() {
        assertThat(LeitnerEngine.INTERVAL_DAYS).containsExactly(1, 3, 7, 14, 30);
    }

    @Test
    void aFirstRightAnswerStartsInBoxTwoAndAFirstWrongOneInBoxOne() {
        assertThat(LeitnerEngine.next(null, true, D1)).isEqualTo(new Card(2, D1.plusDays(3)));
        assertThat(LeitnerEngine.next(null, false, D1)).isEqualTo(new Card(1, D1.plusDays(1)));
    }

    @Test
    void aRightAnswerWhenDueMovesUpOneBoxAndAWrongOneGoesBackToBoxOne() {
        Card box2 = new Card(2, D1);
        assertThat(LeitnerEngine.next(box2, true, D1)).isEqualTo(new Card(3, D1.plusDays(7)));
        assertThat(LeitnerEngine.next(new Card(4, D1), false, D1)).isEqualTo(new Card(1, D1.plusDays(1)));
    }

    @Test
    void theLastBoxKeepsComingBackEveryThirtyDays() {
        assertThat(LeitnerEngine.next(new Card(5, D1), true, D1)).isEqualTo(new Card(5, D1.plusDays(30)));
    }

    @Test
    void answeringRightBeforeTheCardIsDueChangesNothingSoRepeatingAPracticeCannotJumpBoxes() {
        Card box2 = new Card(2, D1.plusDays(3));
        assertThat(LeitnerEngine.next(box2, true, D1)).isSameAs(box2);
        // ...but a wrong answer always counts
        assertThat(LeitnerEngine.next(box2, false, D1)).isEqualTo(new Card(1, D1.plusDays(1)));
    }

    @Test
    void replayingTheHistoryInTimeOrderGivesEachQuestionsCurrentCard() {
        Map<Long, Card> cards = LeitnerEngine.replay(List.of(
                new Event(7L, true, on(D1.plusDays(3))),  // arrives out of order
                new Event(7L, false, on(D1)),
                new Event(8L, true, on(D1))), ZONE);

        assertThat(cards.get(7L)).isEqualTo(new Card(2, D1.plusDays(3 + 3))); // wrong → box 1 due D1+1, right on D1+3 → box 2
        assertThat(cards.get(8L)).isEqualTo(new Card(2, D1.plusDays(3)));
    }

    @Test
    void theDayIsTheSchoolsDayNotUtc() {
        // 23:30 UTC on 30 Sept is already 1 October in Bucharest
        Map<Long, Card> cards = LeitnerEngine.replay(
                List.of(new Event(7L, false, Instant.parse("2026-09-30T23:30:00Z"))), ZONE);
        assertThat(cards.get(7L).dueOn()).isEqualTo(D1.plusDays(1));
    }
}
