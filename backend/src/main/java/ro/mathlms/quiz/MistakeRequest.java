package ro.mathlms.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * The teacher's explanation of a wrong option; blank or null takes it away (and its lesson). {@code lessonId}: the EN
 * lesson page it comes from („De ce am greșit?", V70) and {@code lessonPage} the page printed in the book; both optional.
 */
public record MistakeRequest(@Size(max = QuizOption.MAX_MISTAKE) String mistake, Long lessonId,
                             @Min(1) @Max(5000) Integer lessonPage) {

    public MistakeRequest(String mistake) {
        this(mistake, null, null);
    }
}
