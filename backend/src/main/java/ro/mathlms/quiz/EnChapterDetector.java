package ro.mathlms.quiz;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which EN chapter a statement is about, from the words that give it away ("nu este prim" → A5, "cubul" → G8). Used
 * before the place on the paper: a question about primes sitting at S I.1 must point at divisibility, not at the
 * order of operations that S I.1 usually asks. First rule that matches wins, so the more specific come first (a cube's
 * distance to a plane is G9 before it is a cube; a right triangle is G4 before it is a triangle). Returns null when the
 * words say nothing - then the place decides. Free, instant and offline; also the dev/test chapter suggester.
 */
public final class EnChapterDetector {

    private EnChapterDetector() {
    }

    /** A chapter code and the words that recognised it. */
    public record Match(String code, String words) {
    }

    private record Rule(String code, Pattern pattern) {
    }

    private static Rule rule(String code, String regex) {
        return new Rule(code, Pattern.compile(regex,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS));
    }

    private static final List<Rule> RULES = List.of(
            rule("G10", "trunchi|desfășur|secțiun"),
            rule("G9", "la planul|perpendicular\\w* pe plan|unghiul dreptelor"),
            rule("G8", "\\bcub|prism|piramid|\\bconul\\b|cilindr|sfer|volum|aria laterală|aria totală"),
            rule("A15", "probabil|frecvenț|amplitudin|median[aă]\\b"),
            rule("A14", "gradul al (doilea|II)|x\\^2[^=]*=\\s*0"),
            rule("A13", "reducerii la unitate|metoda comparați|metoda figurativ|mersului invers|falsei ipoteze"),
            rule("A8", "diagram|tabel|grafic\\w* cu bare"),
            rule("A11", "f\\s*\\(\\s*x\\s*\\)|funcți|reprezent\\w* grafic"),
            rule("A10", "E\\s*\\(\\s*x\\s*\\)|expresia E"),
            rule("G6", "cerc|\\barcul|coard"),
            rule("G4", "(?<!trapez )(?<!trapezul )dreptunghic|ipotenuz|pitagora|\\bsin\\b|\\bcos\\b|\\\\sin|\\\\cos"),
            rule("G7", "asemen|thales"),
            rule("G5", "paralelogram|dreptunghi(ul|ului)?\\b|pătrat(ul|ului)?\\b(?!\\s+perfect)|trapez|romb|patrulater"),
            rule("G3", "triunghi"),
            rule("G2", "unghi|bisectoar"),
            rule("G1", "segment|coliniar"),
            rule("A12", "fracți\\w* (ordinar|zecimal)|ireductibil"),
            rule("A3", "%|procent"),
            rule("A9", "\\bani\\b|vârst|\\blei\\b|bănci"),
            rule("A6", "\\\\sqrt|radical|modul|\\bmedi[ae]\\b|aproximar|rotunj"),
            rule("A5", "interval|mulțim|divizib|divizor|multiplu|\\bprim[eă]?\\b|c\\.m\\.m\\.[dm]\\.c"),
            rule("A7", "ecuați"),
            rule("A2", "raport|propor|\\\\frac\\{[a-z]\\}|\\\\frac\\{[^{}]*\\}\\{[a-z]\\}"),
            rule("A4", "opusul|întreg"),
            // Not plain "calcul": "Calculați aria…" is any chapter, and a wrong guess is worse than the place's default.
            rule("A1", "rezultatul calculului|ordinea (efectuării )?operațiilor|puter|pătrat perfect"));

    /** The chapter code the statement is about, or null. */
    public static String detect(String statement) {
        Match match = match(statement);
        return match == null ? null : match.code();
    }

    /** The chapter code and the words that gave it away, or null. */
    public static Match match(String statement) {
        if (statement == null || statement.isBlank()) {
            return null;
        }
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(statement);
            if (m.find()) {
                return new Match(rule.code(), m.group().strip());
            }
        }
        return null;
    }
}
