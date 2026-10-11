package ro.mathlms.review;

import java.time.LocalDate;
import java.util.List;

/** What the daily review (E4) sends to the screens. */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /** An answer choice - without which one is right (that comes back with the result). */
    public record ReviewOptionDto(Long id, int position, String text) {
    }

    /** One question of today's queue. */
    public record ReviewQuestionDto(Long itemId, String quizTitle, String statement, List<ReviewOptionDto> options,
                                    int box) {
    }

    /**
     * The student's review today: at most {@code limit} a day; {@code remainingToday} = the questions left today
     * ({@code questions}); {@code mastered} = cards in the last box.
     */
    public record ReviewTodayDto(int limit, int doneToday, int remainingToday, int streak, int totalCards, int mastered,
                                 List<ReviewQuestionDto> questions) {
    }

    /** One answered review question: the verdict, the right option, the explanation and where the card moved. */
    public record ReviewResultDto(boolean correct, Long correctOptionId, String solution, int boxBefore, int boxAfter,
                                  LocalDate nextDueOn) {
    }

    /** How a student keeps up, for the parent. */
    public record ReviewStatusDto(int doneToday, int remainingToday, int streak, int totalCards, int mastered,
                                  LocalDate lastReviewOn) {
    }

    /** One row of the teacher's class view. */
    public record StudentReviewStatusDto(Long studentId, String fullName, int doneToday, int remainingToday, int streak,
                                         int totalCards, int mastered, LocalDate lastReviewOn) {
    }
}
