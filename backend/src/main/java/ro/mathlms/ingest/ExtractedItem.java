package ro.mathlms.ingest;

import ro.mathlms.quiz.QuizItemType;

import java.util.List;

/**
 * One question as read from a page: what the teacher reviews before it becomes a quiz item. There is deliberately no
 * correct answer - the AI only copies what is printed; the teacher marks the right option and writes the barem.
 *
 * @param page            the photo it is on (1-based, in upload order)
 * @param section         the heading it sits under ("SUBIECTUL I"), or null
 * @param options         a grilă's choices without their letters; empty for an open question
 * @param points          the points printed next to it, or null when none are
 * @param hasFigure       the question refers to a drawing, which the teacher adds as the question's picture
 * @param warnings        what looked wrong when reading it, in Romanian ("formulă incompletă")
 * @param parts           an open problem's requirements a), b)... with their printed points (EN Subiectul III); empty = none
 * @param enChapter       the EN chapter proposed for it ("A1"), or null; the teacher confirms or changes it
 * @param enChapterReason why, in one Romanian sentence ("Un calcul cu paranteze."), or null
 * @param figurePage      the photo the drawing is on (usually {@code page}), or null when there is no usable box
 * @param figureBox       the drawing's box on that photo, {@code [ymin, xmin, ymax, xmax]} on a 0-1000 scale; empty =
 *                        none (the teacher adds the picture by hand, as before)
 */
public record ExtractedItem(int page, String section, QuizItemType type, String statement, List<String> options,
                            Integer points, boolean hasFigure, List<String> warnings, List<Part> parts,
                            String enChapter, String enChapterReason, Integer figurePage, List<Integer> figureBox) {

    /** A reading with a chapter proposal but no figure box (older readings, OpenAI tests). */
    public ExtractedItem(int page, String section, QuizItemType type, String statement, List<String> options,
                         Integer points, boolean hasFigure, List<String> warnings, List<Part> parts,
                         String enChapter, String enChapterReason) {
        this(page, section, type, statement, options, points, hasFigure, warnings, parts, enChapter, enChapterReason,
                null, List.of());
    }

    /** A reading without a chapter proposal (what the page reader returns). */
    public ExtractedItem(int page, String section, QuizItemType type, String statement, List<String> options,
                         Integer points, boolean hasFigure, List<String> warnings, List<Part> parts) {
        this(page, section, type, statement, options, points, hasFigure, warnings, parts, null, null);
    }

    /** A reading from before requirements were read separately. */
    public ExtractedItem(int page, String section, QuizItemType type, String statement, List<String> options,
                         Integer points, boolean hasFigure, List<String> warnings) {
        this(page, section, type, statement, options, points, hasFigure, warnings, List.of());
    }


    /** One requirement: "Arată că $x=25$." worth 2 points (null when none are printed), and its proposed chapter. */
    public record Part(String statement, Integer points, String enChapter, String enChapterReason) {

        public Part(String statement, Integer points) {
            this(statement, points, null, null);
        }

    }
}
