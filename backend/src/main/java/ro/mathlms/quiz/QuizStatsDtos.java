package ro.mathlms.quiz;

import java.util.List;

/** Response DTOs of the teacher's quiz statistics page. */
public final class QuizStatsDtos {

    private QuizStatsDtos() {
    }

    /** One bar of the score distribution, e.g. "60–80%" with the number of graded attempts in it. */
    public record BucketDto(String label, int count) {
    }

    /**
     * How one item went. {@code correctRate} (0..1) only for single-choice items — the lowest rate is the
     * hardest question; {@code averagePoints} for every item. Both are null until something is graded.
     */
    public record ItemStatDto(Long itemId, int position, QuizItemType type, String statement, int points,
                              Double correctRate, Double averagePoints) {
    }

    public record QuizStatsDto(Long quizId, String title, int gradedAttempts, int maxScore,
                               Double averageScore, Integer averagePercent,
                               List<BucketDto> distribution, List<ItemStatDto> items) {
    }
}
