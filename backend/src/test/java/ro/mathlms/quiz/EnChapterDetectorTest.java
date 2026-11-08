package ro.mathlms.quiz;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EnChapterDetectorTest {

    /** What a question is about, from its words - not from where it sits on the paper. */
    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
            Dintre numerele 5, 11, 8 și 17, numărul care nu este prim este:; A5
            Produsul numerelor întregi din intervalul $[1, 4]$ este egal cu:; A5
            Cel mai mare divizor comun al numerelor 12 și 18 este:; A5
            Rezultatul calculului $2^3 + 4 \\cdot 5$ este:; A1
            Dacă $\\frac{a}{4} = \\frac{5}{2}$, atunci a este:; A2
            Un produs costă 200 de lei. După o reducere de 10%, prețul este:; A3
            Opusul numărului -7 este:; A4
            Media aritmetică a numerelor 4 și 10 este:; A6
            Aproximarea prin lipsă la zecimi a numărului $\\sqrt{2}$ este:; A6
            Graficul cu bare de mai jos arată notele elevilor.; A8
            În tabelul de mai jos sunt prezentate temperaturile.; A8
            Se consideră funcția $f(x) = 2x - 3$.; A11
            Pătratul ABCD are latura de 6 cm. Aria pătratului este:; G5
            Dreptunghiul ABCD are lungimea 8 cm.; G5
            Cel mai mic pătrat perfect de două cifre este:; A1
            Triunghiul ABC este dreptunghic în A.; G4
            Unghiurile AOB și BOC sunt suplementare.; G2
            Cubul ABCDA'B'C'D' are muchia 4 cm.; G8
            Piramida triunghiulară regulată VABC are latura bazei 6.; G8
            Dacă $\\frac{4}{a} = \\frac{1}{18}$, atunci numărul real $a$ este egal cu:; A2
            Este reprezentat un trapez dreptunghic $ABCD$ cu $AD \\perp AB$.; G5
            """)
    void findsTheChapterFromTheWords(String statement, String code) {
        assertThat(EnChapterDetector.detect(statement)).isEqualTo(code);
    }

    /** Nothing to go by: the place on the paper decides instead. */
    @ParameterizedTest
    @ValueSource(strings = {"$36 : 4 - 4 \\cdot 2$", "Arată că FO ⊥ DE.", "", "   ",
            "Calculați câți metri pătrați de pânză sunt necesari pentru cort."})
    void staysOutWhenTheWordsSayNothing(String statement) {
        assertThat(EnChapterDetector.detect(statement)).isNull();
    }

    @org.junit.jupiter.api.Test
    void nullIsNothing() {
        assertThat(EnChapterDetector.detect(null)).isNull();
    }
}
