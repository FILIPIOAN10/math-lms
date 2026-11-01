package ro.mathlms.ingest;

import ro.mathlms.ingest.MistakeSuggester.Question;

/** The prompt and the answer's clean-up shared by every {@link MistakeSuggester}. */
final class MistakeReading {

    /** The longest text kept: it must fit the option's column, and a student reads it next to the question. */
    static final int MAX_LENGTH = 400;

    static final String INSTRUCTIONS = """
            Ești profesor de matematică pentru elevi de gimnaziu din România.
            Primești o întrebare de tip grilă, răspunsul corect și o variantă greșită.
            Spune în cel mult două propoziții scurte ce greșeală face elevul care alege această variantă.
            Te adresezi direct elevului, la persoana a doua („Ai adunat numitorii...").
            Numește greșeala concretă, așa cum s-a calculat varianta aleasă, apoi spune ce trebuia făcut.
            Dacă nu vezi cum s-ar ajunge la această variantă, spune doar ce trebuie verificat, fără să inventezi un calcul.
            Scrie formulele în LaTeX între $...$. Răspunzi doar cu textul pentru elev, în română, fără titlu și fără ghilimele.
            """;

    private MistakeReading() {
    }

    static String questionText(Question question) {
        return "Întrebarea:\n" + question.statement().strip()
                + "\n\nRăspunsul corect: " + (question.correct() == null ? "(nemarcat)" : question.correct().strip())
                + "\n\nVarianta greșită aleasă: " + question.wrong().strip();
    }

    /** The answer as a teacher can keep it; a model that said nothing is an error, not an empty box. */
    static String cleaned(String text) throws ExtractionException {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH).strip();
    }
}
