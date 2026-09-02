package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import ro.mathlms.quiz.QuizDtos.ItemDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAdminServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAdminService service =
            new QuizAdminService(quizRepository, itemRepository, optionRepository);

    private final Quiz quiz = new Quiz("Simulare EN", null);

    @Test
    void getQuizThrowsWhenMissing() {
        when(quizRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuiz(404L))
                .isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void addSingleChoiceItemSavesItemAndOptions() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));
        when(optionRepository.save(any(QuizOption.class))).thenAnswer(i -> i.getArgument(0));

        ItemDto dto = service.addItem(1L, new ItemRequest(
                QuizItemType.SINGLE_CHOICE, 1, "Cât e $2+2$?", 5, null,
                List.of(new OptionRequest(0, "$4$", true), new OptionRequest(1, "$5$", false))));

        assertThat(dto.type()).isEqualTo(QuizItemType.SINGLE_CHOICE);
        assertThat(dto.options()).extracting(QuizDtos.OptionDto::text).containsExactly("$4$", "$5$");
        assertThat(dto.options()).filteredOn(QuizDtos.OptionDto::correct)
                .extracting(QuizDtos.OptionDto::text).containsExactly("$4$");
    }

    @Test
    void addSingleChoiceRejectsFewerThanTwoOptions() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.addItem(1L, new ItemRequest(
                QuizItemType.SINGLE_CHOICE, 1, "x", 5, null,
                List.of(new OptionRequest(0, "$4$", true)))))
                .isInstanceOf(InvalidQuizException.class);
        verify(optionRepository, never()).save(any());
    }

    @Test
    void addSingleChoiceRejectsNotExactlyOneCorrect() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));

        // Two correct.
        assertThatThrownBy(() -> service.addItem(1L, new ItemRequest(
                QuizItemType.SINGLE_CHOICE, 1, "x", 5, null,
                List.of(new OptionRequest(0, "A", true), new OptionRequest(1, "B", true)))))
                .isInstanceOf(InvalidQuizException.class);

        // Zero correct.
        assertThatThrownBy(() -> service.addItem(1L, new ItemRequest(
                QuizItemType.SINGLE_CHOICE, 1, "x", 5, null,
                List.of(new OptionRequest(0, "A", false), new OptionRequest(1, "B", false)))))
                .isInstanceOf(InvalidQuizException.class);
        verify(optionRepository, never()).save(any());
    }

    @Test
    void addOpenItemIgnoresOptions() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));

        ItemDto dto = service.addItem(1L, new ItemRequest(
                QuizItemType.OPEN, 3, "Rezolvă.", 30, "barem",
                List.of(new OptionRequest(0, "ignored", false))));

        assertThat(dto.type()).isEqualTo(QuizItemType.OPEN);
        assertThat(dto.options()).isEmpty();
        verify(optionRepository, never()).save(any());
    }

    @Test
    void updateItemRejectsTypeChange() {
        QuizItem open = new QuizItem(quiz, 1, QuizItemType.OPEN, "x", 5, null);
        when(itemRepository.findById(9L)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.updateItem(9L, new ItemRequest(
                QuizItemType.SINGLE_CHOICE, 1, "x", 5, null, List.of())))
                .isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void deleteItemRemovesOptionsThenItem() {
        QuizItem item = new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "x", 5, null);
        when(itemRepository.findById(9L)).thenReturn(Optional.of(item));

        service.deleteItem(9L);

        verify(optionRepository).deleteByItemId(9L);
        verify(itemRepository).delete(item);
    }
}
