package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoCountTest {

    @Test
    void usesTheSingularForOne() {
        assertThat(RoCount.of(1, "carte", "cărți")).isEqualTo("1 carte");
    }

    @Test
    void usesThePluralBelowTwenty() {
        assertThat(RoCount.of(2, "carte", "cărți")).isEqualTo("2 cărți");
        assertThat(RoCount.of(19, "carte", "cărți")).isEqualTo("19 cărți");
        assertThat(RoCount.of(101, "carte", "cărți")).isEqualTo("101 cărți");
    }

    @Test
    void addsDeFromTwentyAndForRoundHundreds() {
        assertThat(RoCount.of(20, "carte", "cărți")).isEqualTo("20 de cărți");
        assertThat(RoCount.of(45, "elev", "elevi")).isEqualTo("45 de elevi");
        assertThat(RoCount.of(100, "carte", "cărți")).isEqualTo("100 de cărți");
    }
}
