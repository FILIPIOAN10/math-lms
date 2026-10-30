package ro.mathlms.student;

import java.util.List;

/**
 * What the list of students adds to each account: the classes and the latest graded test, as points so the page works
 * out the grade the way it does everywhere. Both score fields are {@code null} until a test is graded.
 */
public record StudentListRowDto(Long studentId, List<String> classes, Integer lastScore, Long lastMaxScore) {
}
