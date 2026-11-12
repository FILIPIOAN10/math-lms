package ro.mathlms.review;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemHint;
import ro.mathlms.quiz.QuizItemHintRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.review.ReviewDtos.ReviewOptionDto;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The daily review's and a chapter practice's cards: options and hints in two queries, however many cards. */
class PracticeQuestionsTest {

    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizItemHintRepository hintRepository = mock(QuizItemHintRepository.class);
    private final PracticeQuestions questions = new PracticeQuestions(optionRepository, hintRepository);

    private final Quiz quiz = new Quiz("Fracții", null);

    private QuizItem grila(long id, String statement) {
        QuizItem item = new QuizItem(quiz, (int) id, QuizItemType.SINGLE_CHOICE, statement, 5, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static QuizOption option(QuizItem item, int position, String text, long id) {
        QuizOption option = new QuizOption(item, position, text, position == 1);
        ReflectionTestUtils.setField(option, "id", id);
        return option;
    }

    @Test
    void buildsEveryCardFromOneOptionsQueryAndOneHintsQuery() {
        QuizItem first = grila(100L, "1 + 1 = ?");
        QuizItem second = grila(101L, "2 + 2 = ?");
        when(optionRepository.findByItemIdInOrderByItemIdAscPositionAsc(List.of(100L, 101L))).thenReturn(List.of(
                option(first, 1, "2", 1L), option(first, 2, "3", 2L), option(second, 1, "4", 3L)));
        when(hintRepository.findByItemIdInOrderByItemIdAscPositionAsc(List.of(100L, 101L)))
                .thenReturn(List.of(new QuizItemHint(second, 1, "Numără pe degete."), new QuizItemHint(second, 2, "Doi și doi.")));

        List<ReviewQuestionDto> cards = questions.of(List.of(first, second), item -> item.getId() == 100L ? 2 : 0);

        assertThat(cards).extracting(ReviewQuestionDto::itemId).containsExactly(100L, 101L);
        assertThat(cards.get(0).options()).extracting(ReviewOptionDto::text).containsExactly("2", "3");
        assertThat(cards.get(0).hints()).isEmpty();
        assertThat(cards.get(0).box()).isEqualTo(2);
        assertThat(cards.get(0).quizTitle()).isEqualTo("Fracții");
        assertThat(cards.get(1).options()).extracting(ReviewOptionDto::text).containsExactly("4");
        assertThat(cards.get(1).hints()).containsExactly("Numără pe degete.", "Doi și doi.");
        verify(optionRepository, never()).findByItemIdOrderByPosition(any());
        verify(hintRepository, never()).findByItemIdOrderByPosition(any());
    }

    @Test
    void noCardsNoQueries() {
        assertThat(questions.of(List.of(), item -> 0)).isEmpty();
        verifyNoInteractions(optionRepository, hintRepository);
    }
}
