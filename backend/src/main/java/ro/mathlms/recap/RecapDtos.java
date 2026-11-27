package ro.mathlms.recap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ro.mathlms.quiz.NumericAnswer;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/** What the collection screens send and get. */
public final class RecapDtos {

    private RecapDtos() {
    }

    public record RecapPartDto(String label, String statement, String solution, String answer, String result,
                               boolean verified) {
    }

    /**
     * One exercise for the teacher. {@code verified} = the two AI runs agreed on every requirement (AGREE);
     * {@code usedIn} = how many tests it was put in.
     */
    public record RecapExerciseDto(Long id, String key, int tema, int number, String statement, String solution,
                                   String answer, String result, String aiStatus, boolean verified, boolean hasImage,
                                   boolean figureMissing, Long enChapterId, String enChapterCode, String enChapterTitle,
                                   RecapReview review, int usedIn, List<RecapPartDto> parts) {
        static RecapExerciseDto of(RecapExercise e) {
            List<RecapPart> parts = e.getParts();
            List<RecapPartDto> partDtos = IntStream.range(0, parts.size())
                    .mapToObj(i -> new RecapPartDto(RecapPart.label(i), parts.get(i).getStatement(),
                            parts.get(i).getSolution(), parts.get(i).getAnswer(), parts.get(i).getResult(),
                            parts.get(i).isVerified()))
                    .toList();
            var chapter = e.getEnChapter();
            return new RecapExerciseDto(e.getId(), e.getSourceKey(), e.getTema(), e.getNumber(), e.getStatement(),
                    e.getSolution(), e.getAnswer(), e.getResult(), e.getAiStatus(), "AGREE".equals(e.getAiStatus()),
                    e.getImageKey() != null, e.isFigureMissing(), chapter == null ? null : chapter.getId(),
                    chapter == null ? null : chapter.getCode(), chapter == null ? null : chapter.getTitle(),
                    e.getReview(), e.getQuizIds().size(), partDtos);
        }
    }

    public record PartEdit(@NotBlank @Size(max = 4000) String statement, @Size(max = 8000) String solution,
                           @Size(max = 1000) String answer, @Size(max = NumericAnswer.MAX_LENGTH) String result) {
    }

    /** The teacher's corrections; {@code enChapterId} null = recognised again from the text. */
    public record UpdateRequest(@NotBlank @Size(max = 8000) String statement, @Size(max = 8000) String solution,
                                @Size(max = 1000) String answer, @Size(max = NumericAnswer.MAX_LENGTH) String result,
                                @Valid @Size(max = RecapExercise.MAX_PARTS) List<PartEdit> parts, Long enChapterId) {
    }

    public record ReviewRequest(@NotNull RecapReview review) {
    }

    public record CreateQuizRequest(@NotBlank @Size(max = 200) String title,
                                    @NotEmpty @Size(max = 30) List<@NotNull Long> exerciseIds, Long schoolClassId) {
    }

    /** Catch-up homework on one EN chapter: {@code count} approved exercises, the ones the class has not had first. */
    public record CatchUpRequest(@NotNull Long schoolClassId, @NotNull Long enChapterId,
                                 @NotEmpty List<@NotNull Long> studentIds, @Min(1) @Max(10) int count,
                                 @NotNull Instant dueAt, @Size(max = 200) String title) {
    }

    /** {@code figuresAdded}: drawings given to exercises that were already there and had none. */
    public record ImportResult(int added, int skipped, int total, int figuresAdded) {
    }

    public record CreatedQuizDto(Long quizId) {
    }

    public record CatchUpDto(Long quizId, Long assignmentId, int exerciseCount) {
    }

    /** How many approved exercises each EN chapter has (the catch-up dialog says it before the teacher chooses). */
    public record ChapterCountDto(Long enChapterId, String code, long approved) {
    }
}
