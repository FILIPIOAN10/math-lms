package ro.mathlms.quiz;

import java.time.Instant;

/**
 * The numbers on a row of the teacher's test list: questions, papers handed in, their mean grade (1–10, two decimals;
 * {@code null} until one is graded) and the homework deadline ({@code null} when the quiz is not homework).
 */
public record QuizListSummaryDto(Long quizId, long questions, long papers, Double average, Instant dueAt) {
    static QuizListSummaryDto from(QuizRepository.QuizListRow row) {
        Double average = row.getAverage() == null ? null : Math.round(row.getAverage() * 100) / 100.0;
        return new QuizListSummaryDto(row.getQuizId(), row.getQuestions(), row.getPapers(), average, row.getDueAt());
    }
}
