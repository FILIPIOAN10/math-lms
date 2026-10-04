package ro.mathlms.quiz;

/** The maximum score of one quiz: the sum of its items' points (see {@code QuizItemRepository.sumPointsByQuiz}). */
public record QuizMaxScore(Long quizId, Long maxScore) {
}
