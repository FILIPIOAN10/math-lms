package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.QuizStatsDtos.ItemStatDto;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong();
        List<QuizAttemptRepository.CountedAttemptRow> rows = java.util.Arrays.stream(scores)
                .map(score -> row(ids.incrementAndGet(), score)).toList();
        when(attemptRepository.findCountedAttempts(10L)).thenReturn(rows);
        when(responseRepository.findItemStatsByAttemptIds(any())).thenReturn(List.of());
    }

    private static QuizAttemptRepository.CountedAttemptRow row(long attemptId, int score) {
        return new QuizAttemptRepository.CountedAttemptRow() {
            @Override
            public Long getAttemptId() {
                return attemptId;
            }

            @Override
            public Integer getScore() {
                return score;
            }
        };
    }

    @Test
    void unknownQuizIsNotFound() {
        when(quizRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats(404L)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void aQuizNobodyFinishedHasEmptyStats() {
        givenGradedScores();

        QuizStatsDto stats = service.getStats(10L);

        assertThat(stats.gradedAttempts()).isZero();
        verify(responseRepository, never()).findItemStatsByAttemptIds(any()); // an empty IN list is not portable
        assertThat(stats.averageScore()).isNull();
        assertThat(stats.averagePercent()).isNull();
        assertThat(stats.maxScore()).isEqualTo(15);
        assertThat(stats.distribution()).hasSize(4).allSatisfy(b -> assertThat(b.count()).isZero());
        assertThat(stats.items()).hasSize(2).allSatisfy(i -> {
            assertThat(i.correctRate()).isNull();
            assertThat(i.averagePoints()).isNull();
        });
    }

    @Test
    void averageAndDistributionComeFromTheGradedScores() {
        // max 15 -> grades 10, 8,20, 6,40 and 2,80
        givenGradedScores(15, 12, 9, 3);

        QuizStatsDto stats = service.getStats(10L);

        assertThat(stats.gradedAttempts()).isEqualTo(4);
        assertThat(stats.averageScore()).isEqualTo(9.75);
        assertThat(stats.averagePercent()).isEqualTo(65);
        assertThat(stats.distribution()).extracting(QuizStatsDtos.BucketDto::count)
                .containsExactly(1, 1, 1, 1); // sub 5 (3), 5 – 6,99 (9), 7 – 8,99 (12), 9 – 10 (15)
    }

    @Test
    void eachGroupStartsAtItsGrade() {
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        QuizItem nine = withId(new QuizItem(quiz, 1, QuizItemType.OPEN, "d", 9, null), 102L);
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(nine));
        when(attemptRepository.findCountedAttempts(10L)).thenReturn(List.of(row(1, 3), row(2, 4), row(3, 6), row(4, 8)));
        when(responseRepository.findItemStatsByAttemptIds(any())).thenReturn(List.of());

        // max 9 -> grades 4, 5, 7 and 9: each of the last three opens its group
        assertThat(service.getStats(10L).distribution()).extracting(QuizStatsDtos.BucketDto::count).containsExactly(1, 1, 1, 1);
    }

    @Test
    void aPerfectScoreLandsInTheTopBucket() {
        givenGradedScores(15);

        assertThat(service.getStats(10L).distribution().get(3).count()).isEqualTo(1);
    }

    @Test
    void perItemStatsUseTheNumberOfGradedAttemptsAsDenominator() {
        givenGradedScores(15, 5, 0, 10);
        // grila: answered by 3 of the 4 students, 2 right (the 4th left it blank = counts as wrong)
        // deschis: 4 responses, 30 points awarded in total
        when(responseRepository.findItemStatsByAttemptIds(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of(
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
