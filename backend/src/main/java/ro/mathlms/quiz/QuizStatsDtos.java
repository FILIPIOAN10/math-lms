package ro.mathlms.quiz;

import java.util.List;

/** Response DTOs of the teacher's quiz statistics page. */
public final class QuizStatsDtos {

    private QuizStatsDtos() {
    }

    /** One bar of the score distribution, e.g. "60–80%" with the number of graded attempts in it. */
    public record BucketDto(String label, int count) {
    }

    /** How many students chose one option of a grilă, and what mistake it stands for ({@code mistake} null = not written). */
    public record OptionStatDto(Long id, String letter, String text, boolean correct, int chosen, String mistake) {
    }

    /** The marked answer looks wrong: {@code chosen} of the {@code strong} strongest students agree on {@code letter}. */
    public record SuspectDto(String letter, int chosen, int strong) {
    }

    /**
     * How one item went. {@code correctRate} (0..1) only for single-choice items — the lowest rate is the
     * hardest question; {@code averagePoints} for every item. Both are null until something is graded.
     * {@code options} are empty for an open item, {@code suspect} null unless the answer key needs a second look.
     */
    public record ItemStatDto(Long itemId, int position, QuizItemType type, String statement, int points,
                              Double correctRate, Double averagePoints, String section,
                              List<OptionStatDto> options, SuspectDto suspect) {
        public ItemStatDto {
            options = options == null ? List.of() : options; // a value cached before options existed has none
        }
    }

    public record QuizStatsDto(Long quizId, String title, int gradedAttempts, int maxScore,
                               Double averageScore, Integer averagePercent,
                               List<BucketDto> distribution, List<ItemStatDto> items) {
    }
}
