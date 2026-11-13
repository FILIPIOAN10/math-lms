package ro.mathlms.gradebook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptRepository.CountedGradeRow;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizMaxScore;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The gradebook of one class, for the teacher's own records (the school's catalog is another system): students down,
 * the class's tests across, each cell the grade that counts - the student's highest, as everywhere else in the app.
 * A test nobody in the class has a grade at is left out. Four queries whatever the size of the class.
 */
@Service
public class ClassGradebookService {

    /** One test of the class: a column. */
    public record TestColumn(Long quizId, String title, long maxScore) {
    }

    /** One student: a row. {@code grades.get(i)} belongs to {@code tests.get(i)}; null = no graded paper. */
    public record StudentRow(Long studentId, String fullName, List<BigDecimal> grades) {
    }

    public record Gradebook(String className, List<TestColumn> tests, List<StudentRow> students) {
    }

    private final SchoolClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizAttemptRepository attemptRepository;

    public ClassGradebookService(SchoolClassRepository classRepository, EnrollmentRepository enrollmentRepository,
                                 QuizRepository quizRepository, QuizItemRepository itemRepository,
                                 QuizAttemptRepository attemptRepository) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public Gradebook forClass(Long classId) {
        SchoolClass schoolClass = classRepository.findById(classId)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", classId));
        List<User> students = enrollmentRepository.findBySchoolClassIdFetchStudent(classId).stream()
                .map(Enrollment::getStudent)
                .filter(student -> !student.isErased())
                .toList();
        List<Quiz> quizzes = quizRepository.findOfClass(classId);
        if (students.isEmpty() || quizzes.isEmpty()) {
            return new Gradebook(schoolClass.getName(), List.of(), rows(students, List.of(), Map.of()));
        }

        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();
        Map<Long, Long> maxScores = itemRepository.sumPointsByQuiz(quizIds).stream()
                .collect(Collectors.toMap(QuizMaxScore::quizId, QuizMaxScore::maxScore));
        // quiz id -> student id -> the score that counts
        Map<Long, Map<Long, Integer>> scores = new HashMap<>();
        for (CountedGradeRow row : attemptRepository.findCountedGrades(quizIds,
                students.stream().map(User::getId).toList())) {
            scores.computeIfAbsent(row.getQuizId(), id -> new HashMap<>()).put(row.getStudentId(), row.getScore());
        }

        // Only tests somebody was graded at, and worth something: a test worth no points has no grade.
        List<TestColumn> tests = quizzes.stream()
                .filter(quiz -> scores.containsKey(quiz.getId()) && maxScores.getOrDefault(quiz.getId(), 0L) > 0)
                .map(quiz -> new TestColumn(quiz.getId(), quiz.getTitle(), maxScores.get(quiz.getId())))
                .toList();
        return new Gradebook(schoolClass.getName(), tests, rows(students, tests, scores));
    }

    private static List<StudentRow> rows(List<User> students, List<TestColumn> tests,
                                         Map<Long, Map<Long, Integer>> scores) {
        return students.stream().map(student -> {
            List<BigDecimal> grades = new ArrayList<>();
            for (TestColumn test : tests) {
                Integer score = scores.get(test.quizId()).get(student.getId());
                grades.add(score == null ? null : grade(score, test.maxScore()));
            }
            return new StudentRow(student.getId(), student.getFullName(), grades);
        }).toList();
    }

    /** The 1-10 grade of the app: 1 + 9 x the share of points, to two decimals (lib/grades.ts shows the same). */
    static BigDecimal grade(int score, long maxScore) {
        return BigDecimal.valueOf(Math.round((1 + 9.0 * score / maxScore) * 100), 2);
    }

    /** The file: a header line, then a line per student. Grades with a decimal comma, as a Romanian Excel reads them. */
    public static String toCsv(Gradebook book) {
        StringBuilder csv = new StringBuilder(Csv.BOM);
        List<String> header = new ArrayList<>();
        header.add("Elev");
        book.tests().forEach(test -> header.add(Csv.text(test.title())));
        csv.append(Csv.line(header));
        for (StudentRow student : book.students()) {
            List<String> cells = new ArrayList<>();
            cells.add(Csv.text(student.fullName()));
            student.grades().forEach(grade -> cells.add(grade == null ? "" : formatGrade(grade)));
            csv.append(Csv.line(cells));
        }
        return csv.toString();
    }

    /** "10" for a whole grade, otherwise two decimals with a comma: "5,50", "7,55" - what the screens show. */
    static String formatGrade(BigDecimal grade) {
        BigDecimal scaled = grade.setScale(2, RoundingMode.HALF_UP);
        return scaled.stripTrailingZeros().scale() <= 0
                ? scaled.toBigInteger().toString()
                : scaled.toPlainString().replace('.', ',');
    }
}
