package ro.mathlms.ingest;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The offline stand-in for dev and tests: picks a chapter by keywords, in a fixed order (a cube's distance to a plane
 * is G9 before it is a cube). Good enough to click the flow through; the real proposals come from the AI.
 */
@Component
public class FakeChapterSuggester implements ChapterSuggester {

    private record Rule(String code, Pattern pattern) {
    }

    private static Rule rule(String code, String regex) {
        return new Rule(code, Pattern.compile(regex,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS));
    }

    private static final List<Rule> RULES = List.of(
            rule("G9", "la planul|perpendicular\\w* pe plan|unghiul dreptelor"),
            rule("G8", "\\bcub|prism|piramid|\\bconul\\b|cilindr|sfer|volum|aria laterală|aria totală"),
            rule("A11", "f\\s*\\(\\s*x\\s*\\)|funcți|graficul"),
            rule("A10", "E\\s*\\(\\s*x\\s*\\)|expresia"),
            rule("A8", "diagram|tabel"),
            rule("G6", "cerc|\\barcul|coard"),
            rule("G4", "dreptunghic|ipotenuz|pitagora"),
            rule("G7", "asemen|thales"),
            rule("G5", "paralelogram|dreptunghi|pătrat|trapez|romb|patrulater"),
            rule("G3", "triunghi"),
            rule("G2", "unghi|bisectoar"),
            rule("G1", "segment|coliniar"),
            rule("A3", "%|procent"),
            rule("A9", "\\bani\\b|vârst|\\blei\\b|bănci"),
            rule("A6", "\\\\sqrt|radical|modul"),
            rule("A5", "interval|mulțim|divizibil"),
            rule("A7", "ecuați"),
            rule("A2", "raport|propor|\\\\frac\\{[a-z]\\}"),
            rule("A4", "opusul|întreg"),
            rule("A1", "calcul"));

    @Override
    public String provider() {
        return FakePageExtractor.NAME;
    }

    @Override
    public List<Suggestion> suggest(List<Question> questions, List<Choice> chapters) {
        Set<String> known = chapters.stream().map(Choice::code).collect(Collectors.toSet());
        List<Suggestion> out = new ArrayList<>();
        for (Question q : questions) {
            PartSuggestion whole = match(q.statement() + " " + String.join(" ", q.options()), known);
            List<PartSuggestion> parts = new ArrayList<>();
            for (String part : q.parts()) {
                PartSuggestion own = match(part, known);
                parts.add(own.code() != null ? own : whole);
            }
            out.add(new Suggestion(whole.code(), whole.reason(), parts));
        }
        return out;
    }

    private static PartSuggestion match(String text, Set<String> known) {
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            if (known.contains(rule.code()) && m.find()) {
                return new PartSuggestion(rule.code(), "Recunoscut după „" + m.group().strip() + "”.");
            }
        }
        return new PartSuggestion(null, null);
    }
}
