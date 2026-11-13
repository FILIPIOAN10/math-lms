package ro.mathlms.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterleavingTest {

    /** "a1" belongs to group "a". */
    private static List<String> spread(String... items) {
        return Interleaving.spread(List.of(items), item -> item.substring(0, 1));
    }

    @Test
    void twoQuestionsOfTheSameGroupDoNotFollowEachOtherWhenItCanBeAvoided() {
        assertThat(spread("a1", "a2", "b1", "b2")).containsExactly("a1", "b1", "a2", "b2");
    }

    @Test
    void theBiggestGroupGoesFirstSoItIsNotLeftInARowAtTheEnd() {
        // In the given order it would end with c1 c2 c3; starting from the biggest group avoids every repeat.
        assertThat(spread("a1", "b1", "c1", "c2", "c3")).containsExactly("c1", "a1", "c2", "b1", "c3");
    }

    @Test
    void oneGroupKeepsItsOrder() {
        assertThat(spread("a1", "a2", "a3")).containsExactly("a1", "a2", "a3");
    }

    @Test
    void aGroupTooBigToSeparateRepeatsOnlyAtTheEnd() {
        assertThat(spread("a1", "a2", "a3", "b1")).containsExactly("a1", "b1", "a2", "a3");
    }

    @Test
    void nothingIsLostOrInvented() {
        List<String> items = List.of("a1", "b1", "a2", "c1", "b2", "a3", "d1");
        assertThat(spread(items.toArray(String[]::new))).containsExactlyInAnyOrderElementsOf(items);
        assertThat(Interleaving.spread(List.<String>of(), item -> item)).isEmpty();
    }
}
