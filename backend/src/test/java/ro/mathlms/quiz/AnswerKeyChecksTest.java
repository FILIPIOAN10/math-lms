package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import ro.mathlms.quiz.QuizStatsDtos.ItemStatDto;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;
import ro.mathlms.quiz.QuizStatsDtos.SuspectDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerKeyChecksTest {

    private final QuizStatsService statsService = mock(QuizStatsService.class);
    private final AnswerKeyChecks checks = new AnswerKeyChecks(statsService);

    private static ItemStatDto item(long id, SuspectDto suspect) {
        return new ItemStatDto(id, (int) id, QuizItemType.SINGLE_CHOICE, "x", 5, 0.5, 2.5, null, List.of(), suspect, null, List.of());
    }

    @Test
    void aTestWithFewPapersIsNotCheckedAtAll() {
        QuizListSummaryDto row = new QuizListSummaryDto(7L, 3, SuspectAnswer.MIN_STUDENTS - 1, 6.0, null, 0);

        assertThat(checks.withToCheck(List.of(row))).containsExactly(row);
        verify(statsService, never()).getStats(anyLong());
    }

    @Test
    void countsTheQuestionsWhoseMarkedAnswerLooksWrong() {
        when(statsService.getStats(7L)).thenReturn(new QuizStatsDto(7L, "Teză", 6, 15, 7.0, 47, List.of(), List.of(
                item(1, new SuspectDto("b", 3, 3)), item(2, null), item(3, new SuspectDto("c", 2, 3)))));
        QuizListSummaryDto row = new QuizListSummaryDto(7L, 3, SuspectAnswer.MIN_STUDENTS, 6.0, null, 0);

        assertThat(checks.withToCheck(List.of(row))).singleElement()
                .satisfies(checked -> {
                    assertThat(checked.toCheck()).isEqualTo(2);
                    assertThat(checked.papers()).isEqualTo(SuspectAnswer.MIN_STUDENTS);
                });
    }
}
