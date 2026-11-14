package ro.mathlms.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ro.mathlms.enchapter.EnChapterLinkDto;

import java.util.List;
import java.util.Map;

/** The requirements a), b)... of an open problem, as each screen needs them. */
public final class PartDtos {

    private PartDtos() {
    }

    /** One requirement when authoring; the label follows from its place in the list. */
    public record PartRequest(
            @NotBlank @Size(max = 4000) String statement,
            @Min(0) @Max(100) int points,
            @Size(max = 4000) String solution,
            Long enChapterId, // the EN chapter page for this requirement; null = the default (V34)
            // The final result it should reach, a number (V72); blank = the student is not asked to type one.
            @Size(max = NumericAnswer.MAX_LENGTH) String finalAnswer
    ) {
        public PartRequest(String statement, int points, String solution) {
            this(statement, points, solution, null, null);
        }

        public PartRequest(String statement, int points, String solution, Long enChapterId) {
            this(statement, points, solution, enChapterId, null);
        }
    }

    /**
     * The builder view (with the barem). {@code enChapterId} = the teacher's choice (null = automatic); {@code
     * enChapterAuto} = what "automatic" points at here, so the editor can say it. {@code finalAnswer} = the result the
     * student is asked to type (null = none).
     */
    public record PartDto(Long id, int position, String label, String statement, int points, String solution,
                          Long enChapterId, Long enChapterAuto, String finalAnswer) {
        static PartDto from(QuizItemPart part, EnChapterLinks.Resolved links) {
            return new PartDto(part.getId(), part.getPosition(), part.label(), part.getStatement(), part.getPoints(),
                    part.getSolution(), part.getEnChapter() == null ? null : part.getEnChapter().getId(),
                    links == null ? null : EnChapterLinks.idOf(links.partDefault(part)), part.getFinalAnswer());
        }

        static List<PartDto> of(List<QuizItemPart> parts, EnChapterLinks.Resolved links) {
            return parts.stream().map(p -> from(p, links)).toList();
        }
    }

    /**
     * What the student sees while answering: never the barem, and never the expected result - only whether the
     * requirement asks them to type one ({@code asksResult}).
     */
    public record StudentPartDto(Long id, String label, String statement, int points, boolean asksResult) {
        static List<StudentPartDto> of(List<QuizItemPart> parts) {
            return parts.stream()
                    .map(p -> new StudentPartDto(p.getId(), p.label(), p.getStatement(), p.getPoints(), p.asksResult()))
                    .toList();
        }
    }

    /**
     * A graded (or being graded) requirement: its barem and the points given for it, null until then, and the EN
     * chapter page to open when points were lost (null = none). {@code typedResult} = the final result the student
     * typed (null = none, or not asked), {@code expectedResult} = the barem's, and {@code resultCheck} what comparing
     * the two says (null = the requirement asks for no result): a hint for the grader, never points by itself.
     */
    public record GradedPartDto(Long id, String label, String statement, int points, String barem, Integer awardedPoints,
                                EnChapterLinkDto enChapter, String typedResult, String expectedResult,
                                ResultCheck resultCheck) {
        static List<GradedPartDto> of(List<QuizItemPart> parts, ItemResponse response) {
            return of(parts, response, null);
        }

        static List<GradedPartDto> of(List<QuizItemPart> parts, ItemResponse response, EnChapterLinks.Resolved links) {
            Map<Long, Integer> given = response == null ? Map.of() : response.getPartPoints();
            Map<Long, String> typed = response == null ? Map.of() : response.getPartAnswers();
            return parts.stream().map(p -> {
                // A result typed before the teacher stopped asking for one is not shown: nothing to compare it with.
                String result = p.asksResult() ? typed.get(p.getId()) : null;
                return new GradedPartDto(p.getId(), p.label(), p.getStatement(), p.getPoints(), p.getSolution(),
                        given.get(p.getId()), links == null ? null : links.linkOf(p), result, p.getFinalAnswer(),
                        ResultCheck.of(p.getFinalAnswer(), result));
            }).toList();
        }

        /**
         * The same requirement without its barem, its expected result and the verdict on the typed one - each gives the
         * answer away, and the student may still hand the test in again (see AnswerKeys). What they typed stays.
         */
        GradedPartDto withoutBarem() {
            return new GradedPartDto(id, label, statement, points, null, awardedPoints, enChapter, typedResult, null,
                    null);
        }
    }

    /** The teacher's points for one requirement; null takes them away. */
    public record PartGradeRequest(@NotNull Long partId, @Min(0) Integer points) {
    }
}
