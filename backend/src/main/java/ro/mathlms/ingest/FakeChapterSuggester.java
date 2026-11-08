package ro.mathlms.ingest;

import org.springframework.stereotype.Component;
import ro.mathlms.quiz.EnChapterDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The offline stand-in for dev and tests: picks a chapter by keywords ({@link EnChapterDetector}, the same rules that
 * link a question by its words before its place). The real proposals come from the AI.
 */
@Component
public class FakeChapterSuggester implements ChapterSuggester {

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
        EnChapterDetector.Match m = EnChapterDetector.match(text);
        if (m == null || !known.contains(m.code())) {
            return new PartSuggestion(null, null);
        }
        return new PartSuggestion(m.code(), "Recunoscut după „" + m.words() + "”.");
    }
}
