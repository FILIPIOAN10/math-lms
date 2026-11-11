package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptOrderTest {

    private record Item(int n, String section) {
    }

    private static List<Item> plain(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(n -> new Item(n, null)).toList();
    }

    @Test
    void theSameSeedGivesTheSameOrderAndAnotherSeedAnotherOne() {
        List<Item> items = plain(10);

        List<Item> first = AttemptOrder.withinSections(items, Item::section, 41L);

        assertThat(AttemptOrder.withinSections(items, Item::section, 41L)).isEqualTo(first);
        assertThat(first).containsExactlyInAnyOrderElementsOf(items).isNotEqualTo(items);
        assertThat(AttemptOrder.withinSections(items, Item::section, 42L)).isNotEqualTo(first);
    }

    @Test
    void questionsMoveOnlyWithinTheirSection() {
        List<Item> items = List.of(
                new Item(1, "SUBIECTUL I"), new Item(2, "SUBIECTUL I"), new Item(3, "SUBIECTUL I"),
                new Item(4, "SUBIECTUL II"), new Item(5, "SUBIECTUL II"),
                new Item(6, "SUBIECTUL III"));

        for (long seed = 1; seed < 30; seed++) {
            List<Item> shuffled = AttemptOrder.withinSections(items, Item::section, seed);
            assertThat(shuffled).extracting(Item::section).containsExactly(
                    "SUBIECTUL I", "SUBIECTUL I", "SUBIECTUL I", "SUBIECTUL II", "SUBIECTUL II", "SUBIECTUL III");
            assertThat(shuffled.subList(0, 3)).extracting(Item::n).containsExactlyInAnyOrder(1, 2, 3);
        }
    }

    private QuizAttempt attempt(Quiz quiz, long id) {
        QuizAttempt attempt = new QuizAttempt(quiz, new User("e@scoala.ro", "Elev", Role.STUDENT), Instant.now());
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }

    @Test
    void anAttemptKeepsTheSwitchesItStartedWith() {
        Quiz quiz = new Quiz("Teză", null);
        quiz.shuffle(true, true);
        QuizAttempt started = attempt(quiz, 7L);

        quiz.shuffle(false, false);

        assertThat(started.isShuffleItems()).isTrue();
        assertThat(started.isShuffleOptions()).isTrue();
        assertThat(attempt(quiz, 8L).isShuffleItems()).isFalse();
    }

    @Test
    void neighbouringAttemptsGetDifferentOptionOrders() {
        Quiz quiz = new Quiz("Teză", null);
        quiz.shuffle(true, true);
        QuizItem item = new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", 1, null);
        List<QuizOption> options = IntStream.rangeClosed(1, 4).mapToObj(n -> new QuizOption(item, n, "o" + n, n == 1)).toList();

        // 20 classmates whose attempts were started one after the other: their orders must not all look alike.
        long distinct = java.util.stream.LongStream.rangeClosed(19, 38)
                .mapToObj(id -> AttemptOrder.options(attempt(quiz, id), 7L, options))
                .distinct().count();
        assertThat(distinct).isGreaterThan(8);
        assertThat(AttemptOrder.options(attempt(quiz, 19L), 7L, options))
                .isNotEqualTo(AttemptOrder.options(attempt(quiz, 20L), 7L, options));
    }

    @Test
    void withTheSwitchesOffTheTeachersOrderIsKept() {
        Quiz quiz = new Quiz("Teză", null);
        List<QuizItem> items = IntStream.rangeClosed(1, 6)
                .mapToObj(n -> new QuizItem(quiz, n, QuizItemType.SINGLE_CHOICE, "s" + n, 1, null)).toList();
        List<QuizOption> options = IntStream.rangeClosed(1, 4)
                .mapToObj(n -> new QuizOption(items.get(0), n, "o" + n, n == 1)).toList();
        QuizAttempt attempt = attempt(quiz, 7L);

        assertThat(AttemptOrder.items(attempt, items)).isSameAs(items);
        assertThat(AttemptOrder.options(attempt, 1L, options)).isSameAs(options);
    }
}
