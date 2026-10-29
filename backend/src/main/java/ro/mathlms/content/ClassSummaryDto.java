package ro.mathlms.content;

/**
 * The numbers on a class card: enrolled students, tests reserved for the class, and the mean grade (1–10, two
 * decimals) of its graded tests - {@code null} until one is graded.
 */
public record ClassSummaryDto(Long classId, long students, long quizzes, Double average) {
    static ClassSummaryDto from(SchoolClassRepository.ClassSummaryRow row) {
        Double average = row.getAverage() == null ? null : Math.round(row.getAverage() * 100) / 100.0;
        return new ClassSummaryDto(row.getClassId(), row.getStudents(), row.getQuizzes(), average);
    }
}
