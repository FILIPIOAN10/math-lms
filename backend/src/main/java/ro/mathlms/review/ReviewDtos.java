package ro.mathlms.review;

import ro.mathlms.enchapter.EnChapterLinkDto;

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
                                    int box, String image) {
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
                                  LocalDate nextDueOn,
                                  String mistake, // the teacher's words about the wrong option chosen; null = none
                                  EnChapterLinkDto enChapter // the lesson to revise; only after a wrong answer
    ) {
    }

    /**
     * Practising one EN chapter from its page: up to five grile linked to it, the ones answered wrong last time first;
     * {@code available} = how many the chapter has for this student in all (0 = nothing to practise yet).
     */
    public record ChapterPracticeDto(Long chapterId, String code, String title, int available,
                                     List<ReviewQuestionDto> questions) {
    }

    /** One checked answer of a chapter practice: nothing is stored, so there is no box to move. */
    public record ChapterPracticeResultDto(boolean correct, Long correctOptionId, String solution,
                                           String mistake // the teacher's words about the wrong option chosen; null = none
    ) {
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
