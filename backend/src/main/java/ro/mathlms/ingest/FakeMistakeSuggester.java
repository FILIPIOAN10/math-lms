package ro.mathlms.ingest;

import org.springframework.stereotype.Component;

/** The offline stand-in for dev and tests: enough to click the flow through, without a key. */
@Component
public class FakeMistakeSuggester implements MistakeSuggester {

    @Override
    public String provider() {
        return FakePageExtractor.NAME;
    }

    @Override
    public String suggest(Question question) {
        return "Ai ales " + question.wrong().strip() + ". Refă calculul pas cu pas și compară cu fiecare variantă.";
    }
}
