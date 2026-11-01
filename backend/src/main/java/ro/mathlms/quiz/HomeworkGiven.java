package ro.mathlms.quiz;

import java.time.Instant;

/** When a quiz was last given as homework to a class: the attempts before that do not count against the student. */
public record HomeworkGiven(Long quizId, Instant givenAt) {
}
