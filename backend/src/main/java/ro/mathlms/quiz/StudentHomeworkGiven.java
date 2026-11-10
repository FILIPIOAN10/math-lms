package ro.mathlms.quiz;

import java.time.Instant;

/** {@link HomeworkGiven} for one of several students at once: when the quiz was last given to them as homework. */
public record StudentHomeworkGiven(Long studentId, Long quizId, Instant givenAt) {
}
