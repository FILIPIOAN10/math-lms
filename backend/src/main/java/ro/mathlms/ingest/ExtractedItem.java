package ro.mathlms.ingest;

import ro.mathlms.quiz.QuizItemType;

import java.util.List;

/**
 * One question as read from a page: what the teacher reviews before it becomes a quiz item. There is deliberately no
 * correct answer - the AI only copies what is printed; the teacher marks the right option and writes the barem.
 *
 * @param page      the photo it is on (1-based, in upload order)
 * @param section   the heading it sits under ("SUBIECTUL I"), or null
 * @param options   a grilă's choices without their letters; empty for an open question
 * @param points    the points printed next to it, or null when none are
 * @param hasFigure the question refers to a drawing, which the teacher adds as the question's picture
 * @param warnings  what looked wrong when reading it, in Romanian ("formulă incompletă")
 */
public record ExtractedItem(int page, String section, QuizItemType type, String statement, List<String> options,
                            Integer points, boolean hasFigure, List<String> warnings) {
}
