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
            Long enChapterId // the EN chapter page for this requirement; null = the default (V34)
    ) {
        public PartRequest(String statement, int points, String solution) {
            this(statement, points, solution, null);
        }
    }

    /**
     * The builder view (with the barem). {@code enChapterId} = the teacher's choice (null = automatic); {@code
     * enChapterAuto} = what "automatic" points at here, so the editor can say it.
     */
    public record PartDto(Long id, int position, String label, String statement, int points, String solution,
                          Long enChapterId, Long enChapterAuto) {
        static PartDto from(QuizItemPart part, EnChapterLinks.Resolved links) {
            return new PartDto(part.getId(), part.getPosition(), part.label(), part.getStatement(), part.getPoints(),
                    part.getSolution(), part.getEnChapter() == null ? null : part.getEnChapter().getId(),
                    links == null ? null : EnChapterLinks.idOf(links.partDefault(part)));
        }

        static List<PartDto> of(List<QuizItemPart> parts, EnChapterLinks.Resolved links) {
            return parts.stream().map(p -> from(p, links)).toList();
        }
    }

    /** What the student sees while answering: never the barem. */
    public record StudentPartDto(Long id, String label, String statement, int points) {
        static List<StudentPartDto> of(List<QuizItemPart> parts) {
            return parts.stream()
                    .map(p -> new StudentPartDto(p.getId(), p.label(), p.getStatement(), p.getPoints())).toList();
        }
    }

    /**
     * A graded (or being graded) requirement: its barem and the points given for it, null until then, and the EN
     * chapter page to open when points were lost (null = none).
     */
    public record GradedPartDto(Long id, String label, String statement, int points, String barem, Integer awardedPoints,
                                EnChapterLinkDto enChapter) {
        static List<GradedPartDto> of(List<QuizItemPart> parts, ItemResponse response) {
            return of(parts, response, null);
        }

        static List<GradedPartDto> of(List<QuizItemPart> parts, ItemResponse response, EnChapterLinks.Resolved links) {
            Map<Long, Integer> given = response == null ? Map.of() : response.getPartPoints();
            return parts.stream().map(p -> new GradedPartDto(p.getId(), p.label(), p.getStatement(), p.getPoints(),
                    p.getSolution(), given.get(p.getId()), links == null ? null : links.linkOf(p))).toList();
        }
    }

    /** The teacher's points for one requirement; null takes them away. */
    public record PartGradeRequest(@NotNull Long partId, @Min(0) Integer points) {
    }
}
