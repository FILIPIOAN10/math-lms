package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.QuizStatsDtos.ItemStatDto;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuizStatsServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final QuizStatsService service =
            new QuizStatsService(quizRepository, itemRepository, attemptRepository, responseRepository);

    private final Quiz quiz = withId(new Quiz("Simulare EN", null), 10L);
    private final QuizItem grila = withId(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "g", 5, null), 100L);
    private final QuizItem deschis = withId(new QuizItem(quiz, 2, QuizItemType.OPEN, "d", 10, "barem"), 101L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void givenGradedScores(Integer... scores) {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(grila, deschis));
        when(attemptRepository.findGradedScoresByQuizId(10L)).thenReturn(List.of(scores));
    }

    @Test
    void unknownQuizIsNotFound() {
        when(quizRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats(404L)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void aQuizNobodyFinishedHasEmptyStats() {
        givenGradedScores();
        when(responseRepository.findItemStatsByQuizId(10L)).thenReturn(List.of());

        QuizStatsDto stats = service.getStats(10L);

        assertThat(stats.gradedAttempts()).isZero();
        assertThat(stats.averageScore()).isNull();
        assertThat(stats.averagePercent()).isNull();
        assertThat(stats.maxScore()).isEqualTo(15);
        assertThat(stats.distribution()).hasSize(5).allSatisfy(b -> assertThat(b.count()).isZero());
        assertThat(stats.items()).hasSize(2).allSatisfy(i -> {
            assertThat(i.correctRate()).isNull();
            assertThat(i.averagePoints()).isNull();
        });
    }

    @Test
    void averageAndDistributionComeFromTheGradedScores() {
        // max 15 -> 15 = 100 %, 12 = 80 %, 9 = 60 %, 3 = 20 %
        givenGradedScores(15, 12, 9, 3);
        when(responseRepository.findItemStatsByQuizId(10L)).thenReturn(List.of());

        QuizStatsDto stats = service.getStats(10L);

        assertThat(stats.gradedAttempts()).isEqualTo(4);
        assertThat(stats.averageScore()).isEqualTo(9.75);
        assertThat(stats.averagePercent()).isEqualTo(65);
        assertThat(stats.distribution()).extracting(QuizStatsDtos.BucketDto::count)
                .containsExactly(0, 1, 0, 1, 2); // 0-20, 20-40 (3), 40-60, 60-80 (9), 80-100 (12, 15)
    }

    @Test
    void aPerfectScoreLandsInTheTopBucket() {
        givenGradedScores(15);
        when(responseRepository.findItemStatsByQuizId(10L)).thenReturn(List.of());

        assertThat(service.getStats(10L).distribution().get(4).count()).isEqualTo(1);
    }

    @Test
    void perItemStatsUseTheNumberOfGradedAttemptsAsDenominator() {
        givenGradedScores(15, 5, 0, 10);
        // grila: answered by 3 of the 4 students, 2 right (the 4th left it blank = counts as wrong)
        // deschis: 4 responses, 30 points awarded in total
        when(responseRepository.findItemStatsByQuizId(10L)).thenReturn(List.of(
                new ItemStat(100L, 3L, 2L, 10L),
                new ItemStat(101L, 4L, 0L, 30L)));

        List<ItemStatDto> items = service.getStats(10L).items();

        assertThat(items.get(0).itemId()).isEqualTo(100L);
        assertThat(items.get(0).correctRate()).isEqualTo(0.5);      // 2 / 4
        assertThat(items.get(0).averagePoints()).isEqualTo(2.5);    // 10 / 4
        assertThat(items.get(1).correctRate()).isNull();            // open items have no right/wrong
        assertThat(items.get(1).averagePoints()).isEqualTo(7.5);    // 30 / 4
    }
}
