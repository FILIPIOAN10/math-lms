package ro.mathlms.quiz;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Which tests have a grilă whose marked answer looks wrong (see {@link SuspectAnswer}), for the teacher's test list and
 * Acasă: a wrong key marks every paper wrong, so the warning must not wait until the teacher happens to open Statistici.
 * Read from the cached statistics, and only for a test with enough papers for the check to say anything.
 */
@Service
public class AnswerKeyChecks {

    private final QuizStatsService statsService;

    public AnswerKeyChecks(QuizStatsService statsService) {
        this.statsService = statsService;
    }

    public List<QuizListSummaryDto> withToCheck(List<QuizListSummaryDto> rows) {
        return rows.stream().map(this::withToCheck).toList();
    }

    private QuizListSummaryDto withToCheck(QuizListSummaryDto row) {
        // Papers count retakes too, so a test under the threshold here is under it in the statistics as well.
        if (row.papers() < SuspectAnswer.MIN_STUDENTS) {
            return row;
        }
        long suspects = statsService.getStats(row.quizId()).items().stream().filter(item -> item.suspect() != null).count();
        return row.withToCheck((int) suspects);
    }
}
