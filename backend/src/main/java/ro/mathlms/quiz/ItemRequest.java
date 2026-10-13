package ro.mathlms.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create/update payload for a quiz item. For a {@link QuizItemType#SINGLE_CHOICE} item,
 * {@code options} must have at least two entries with exactly one correct (checked in the
 * service); for {@link QuizItemType#OPEN}, {@code options} is ignored and {@code parts} may split it into
 * requirements a), b)... graded one by one.
 */
public record ItemRequest(
        @NotNull QuizItemType type,
        int position,
        @NotBlank String statement,
        @Min(0) int points,
        String solution,
        @Valid List<OptionRequest> options,
        @Size(max = QuizItem.MAX_HINTS) List<@NotBlank @Size(max = 2000) String> hints, // optional progressive hints, in order
        Long chapterId, // optional: overrides the quiz's chapter for this question; null = inherit (E6)
        @Size(max = QuizItem.MAX_SECTION) String section, // optional heading it sits under ("SUBIECTUL I"); null = none
        Boolean imageRequired, // optional: the question still needs its picture (import "[imagine]"); only read on create
        // optional, OPEN only: the requirements a), b)... with their own points and barem (EN Subiectul III); when given,
        // the item's points are their sum and {@code points} is ignored
        @Valid @Size(max = QuizItemPart.MAX_PARTS) List<PartDtos.PartRequest> parts,
        Long enChapterId // optional: the EN chapter page for this question; null = the default for its place (V34)
) {
    /** An item without its own EN chapter (what the builder sent before V34). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints, Long chapterId, String section,
                       Boolean imageRequired, List<PartDtos.PartRequest> parts) {
        this(type, position, statement, points, solution, options, hints, chapterId, section, imageRequired, parts, null);
    }

    /** An item without requirements a), b) (what the builder sent before V32). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints, Long chapterId, String section,
                       Boolean imageRequired) {
        this(type, position, statement, points, solution, options, hints, chapterId, section, imageRequired, null);
    }

    /** An item that needs no picture (what the builder sent before question images). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints, Long chapterId, String section) {
        this(type, position, statement, points, solution, options, hints, chapterId, section, null);
    }

    /** An item without a section heading (what the builder sent before EN sections). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints, Long chapterId) {
        this(type, position, statement, points, solution, options, hints, chapterId, null, null);
    }

    /** An item without hints (what the builder sent before E3). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options) {
        this(type, position, statement, points, solution, options, null, null, null, null);
    }

    /** An item without its own chapter (what the builder sent before E6). */
    public ItemRequest(QuizItemType type, int position, String statement, int points, String solution,
                       List<OptionRequest> options, List<String> hints) {
        this(type, position, statement, points, solution, options, hints, null, null, null);
    }
}
