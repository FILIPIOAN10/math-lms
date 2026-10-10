package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
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
    private final QuizItemHintRepository hintRepository = mock(QuizItemHintRepository.class);
    private final SchoolClassRepository schoolClassRepository = mock(SchoolClassRepository.class);
    private final AfterCommitCacheEvictor cacheEvictor = mock(AfterCommitCacheEvictor.class);
    private final QuizAdminService service =
            new QuizAdminService(quizRepository, itemRepository, optionRepository, hintRepository, schoolClassRepository, cacheEvictor);

    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);

    @Test
    void changingItemsDropsTheStatsAndProgressCaches() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));

        service.addItem(1L, new ItemRequest(QuizItemType.OPEN, 1, "deschis", 10, null, null));

        verify(cacheEvictor).evictAll(CacheNames.QUIZ_STATS);
        verify(cacheEvictor).evictAll(CacheNames.PROGRESS);
    }

    @Test
    void createQuizWithoutClassIsForEveryone() {
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        Quiz created = service.createQuiz("Simulare EN", "d", null, null, false);

        assertThat(created.getSchoolClass()).isNull();
        verify(schoolClassRepository, never()).findById(any());
    }

    @Test
    void createQuizAssignsTheChosenClass() {
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.of(ninth));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        Quiz created = service.createQuiz("Simulare EN", "d", 5L, null, false);

        assertThat(created.getSchoolClass()).isSameAs(ninth);
    }

    @Test
    void createQuizRejectsAnUnknownClass() {
        when(schoolClassRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createQuiz("Simulare EN", "d", 404L, null, false))
                .isInstanceOf(QuizNotFoundException.class);
        verify(quizRepository, never()).save(any());
    }

    @Test
    void updateQuizCanMoveItToAnotherClassAndBackToEveryone() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.of(ninth));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.updateQuiz(1L, "Nou", "d", 5L, null, false).getSchoolClass()).isSameAs(ninth);
        assertThat(service.updateQuiz(1L, "Nou", "d", null, null, false).getSchoolClass()).isNull();
    }

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

    @Test
    void createQuizStoresTheTimeLimit() {
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        Quiz created = service.createQuiz("Simulare EN", "d", null, 45, false);

        assertThat(created.getTimeLimitMinutes()).isEqualTo(45);
    }

    @Test
    void updateQuizCanSetAndClearTheTimeLimit() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.updateQuiz(1L, "Nou", "d", null, 30, false).getTimeLimitMinutes()).isEqualTo(30);
        assertThat(service.updateQuiz(1L, "Nou", "d", null, null, false).getTimeLimitMinutes()).isNull();
    }

    @Test
    void anOutOfRangeTimeLimitIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.createQuiz("Simulare EN", "d", null, 0, false))
                .isInstanceOf(IllegalArgumentException.class);

        verify(quizRepository, never()).save(any());
    }

    @Test
    void aNewQuizDoesNotAllowPracticeUnlessTheTeacherTicksIt() {
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.createQuiz("Simulare EN", "d", null, null, false).isPracticeAllowed()).isFalse();
        assertThat(service.createQuiz("Simulare EN", "d", null, null, true).isPracticeAllowed()).isTrue();
    }

    @Test
    void updateQuizCanTurnPracticeOnAndOff() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.updateQuiz(1L, "Nou", "d", null, null, true).isPracticeAllowed()).isTrue();
        assertThat(service.updateQuiz(1L, "Nou", "d", null, null, false).isPracticeAllowed()).isFalse();
    }

    // --- E3 hints ---

    private ItemRequest openWithHints(List<String> hints) {
        return new ItemRequest(QuizItemType.OPEN, 1, "deschis", 10, null, null, hints);
    }

    @Test
    void anItemsHintsAreStoredInTheOrderGivenWithPositionsFromOne() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));
        when(hintRepository.save(any(QuizItemHint.class))).thenAnswer(i -> i.getArgument(0));

        ItemDto dto = service.addItem(1L, openWithHints(List.of("primul", "al doilea")));

        ArgumentCaptor<QuizItemHint> saved = ArgumentCaptor.forClass(QuizItemHint.class);
        verify(hintRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(QuizItemHint::getPosition).containsExactly(1, 2);
        assertThat(saved.getAllValues()).extracting(QuizItemHint::getText).containsExactly("primul", "al doilea");
        assertThat(dto.hints()).containsExactly("primul", "al doilea");
    }

    @Test
    void anItemWithoutHintsStoresNone() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));

        ItemDto dto = service.addItem(1L, new ItemRequest(QuizItemType.OPEN, 1, "deschis", 10, null, null));

        assertThat(dto.hints()).isEmpty();
        verify(hintRepository, never()).save(any());
    }

    @Test
    void moreThanFiveHintsAreRejectedAndNothingIsSaved() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> service.addItem(1L, openWithHints(List.of("1", "2", "3", "4", "5", "6"))))
                .isInstanceOf(InvalidQuizException.class);

        verify(itemRepository, never()).save(any());
        verify(hintRepository, never()).save(any());
    }

    @Test
    void aBlankHintIsRejected() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> service.addItem(1L, openWithHints(List.of("bun", "  "))))
                .isInstanceOf(InvalidQuizException.class);

        verify(hintRepository, never()).save(any());
    }

    @Test
    void updatingAnItemReplacesItsHintsWholesale() {
        QuizItem existing = new QuizItem(quiz, 1, QuizItemType.OPEN, "deschis", 10, null);
        when(itemRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(hintRepository.save(any(QuizItemHint.class))).thenAnswer(i -> i.getArgument(0));

        ItemDto dto = service.updateItem(9L, openWithHints(List.of("nou")));

        verify(hintRepository).deleteByItemId(any());
        assertThat(dto.hints()).containsExactly("nou");
    }

    @Test
    void deletingAnItemDeletesItsHintsFirst() {
        QuizItem existing = new QuizItem(quiz, 1, QuizItemType.OPEN, "deschis", 10, null);
        when(itemRepository.findById(9L)).thenReturn(Optional.of(existing));

        service.deleteItem(9L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(hintRepository, itemRepository);
        order.verify(hintRepository).deleteByItemId(any());
        order.verify(itemRepository).delete(existing);
    }

    @Test
    void deleteQuizRefusesOnceStudentsHaveAttemptsAndPointsAtUnpublish() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizRepository.countAttempts(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteQuiz(1L))
                .isInstanceOf(QuizInUseException.class)
                .hasMessageContaining("Simulare EN")
                .hasMessageContaining("2 încercări")
                .hasMessageContaining("Depublică");
        verify(quizRepository, never()).delete(any(Quiz.class));
    }

    @Test
    void deleteQuizRemovesAQuizNobodyTook() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));

        service.deleteQuiz(1L);

        verify(quizRepository).delete(quiz);
    }
}
