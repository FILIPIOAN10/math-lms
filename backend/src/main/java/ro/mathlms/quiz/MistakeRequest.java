package ro.mathlms.quiz;

import jakarta.validation.constraints.Size;

/** The teacher's explanation of a wrong option; blank or null takes it away. */
public record MistakeRequest(@Size(max = QuizOption.MAX_MISTAKE) String mistake) {
}
