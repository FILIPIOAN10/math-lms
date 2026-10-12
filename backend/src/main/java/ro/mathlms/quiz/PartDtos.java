package ro.mathlms.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
            @Size(max = 4000) String solution
    ) {
    }

    /** The builder view (with the barem). */
    public record PartDto(Long id, int position, String label, String statement, int points, String solution) {
        static PartDto from(QuizItemPart part) {
            return new PartDto(part.getId(), part.getPosition(), part.label(), part.getStatement(), part.getPoints(),
                    part.getSolution());
        }

        static List<PartDto> of(List<QuizItemPart> parts) {
            return parts.stream().map(PartDto::from).toList();
        }
    }

    /** What the student sees while answering: never the barem. */
    public record StudentPartDto(Long id, String label, String statement, int points) {
        static List<StudentPartDto> of(List<QuizItemPart> parts) {
            return parts.stream()
                    .map(p -> new StudentPartDto(p.getId(), p.label(), p.getStatement(), p.getPoints())).toList();
        }
    }

    /** A graded (or being graded) requirement: its barem and the points given for it, null until then. */
    public record GradedPartDto(Long id, String label, String statement, int points, String barem, Integer awardedPoints) {
        static List<GradedPartDto> of(List<QuizItemPart> parts, ItemResponse response) {
            Map<Long, Integer> given = response == null ? Map.of() : response.getPartPoints();
            return parts.stream().map(p -> new GradedPartDto(p.getId(), p.label(), p.getStatement(), p.getPoints(),
                    p.getSolution(), given.get(p.getId()))).toList();
        }
    }

    /** The teacher's points for one requirement; null takes them away. */
    public record PartGradeRequest(@NotNull Long partId, @Min(0) Integer points) {
    }
}
