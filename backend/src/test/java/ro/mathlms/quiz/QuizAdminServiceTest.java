package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
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

    // --- M2: moving a quiz that is homework for another class ---

    private final SchoolClass tenth = new SchoolClass("Clasa a 10-a", null);

    @Test
    void movingAQuizToAnotherClassIsRefusedWhileItIsHomeworkForADifferentClass() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(schoolClassRepository.findById(6L)).thenReturn(Optional.of(tenth));
        when(quizRepository.findAssignedClassNamesOtherThan(1L, 6L)).thenReturn(List.of("Clasa a 9-a"));

        assertThatThrownBy(() -> service.updateQuiz(1L, "Simulare EN", null, 6L, null, false))
                .isInstanceOf(QuizInUseException.class)
                .hasMessageContaining("temă pentru Clasa a 9-a")
                .hasMessageContaining("Clasa a 10-a");
        verify(quizRepository, never()).save(any(Quiz.class));
    }

    @Test
    void aQuizThatIsHomeworkCanStillBeOpenedToEveryoneOrKeptOnItsOwnClass() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(schoolClassRepository.findById(5L)).thenReturn(Optional.of(ninth));
        when(quizRepository.findAssignedClassNamesOtherThan(1L, 5L)).thenReturn(List.of());

        service.updateQuiz(1L, "Simulare EN", null, null, null, false);
        service.updateQuiz(1L, "Simulare EN", null, 5L, null, false);

        verify(quizRepository, org.mockito.Mockito.times(2)).save(quiz);
    }

    // --- M3: a quiz students already took keeps its scoring ---

    private QuizItem lockedChoiceItem() {
        ReflectionTestUtils.setField(quiz, "id", 1L);
        QuizItem item = new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 2, null);
        ReflectionTestUtils.setField(item, "id", 7L);
        when(itemRepository.findById(7L)).thenReturn(Optional.of(item));
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizRepository.countAttempts(1L)).thenReturn(2L);
        when(optionRepository.findByItemIdOrderByPosition(7L)).thenReturn(List.of(
                new QuizOption(item, 0, "$4$", true), new QuizOption(item, 1, "$5$", false)));
        return item;
    }

    @Test
    void noItemCanBeAddedOnceStudentsHaveAttempts() {
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        ReflectionTestUtils.setField(quiz, "id", 1L);
        when(quizRepository.countAttempts(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.addItem(1L, new ItemRequest(QuizItemType.OPEN, 2, "Demonstrează", 3, null, null)))
                .isInstanceOf(QuizInUseException.class)
                .hasMessageContaining("2 încercări")
                .hasMessageContaining("nu mai poți adăuga");
        verify(itemRepository, never()).save(any(QuizItem.class));
    }

    @Test
    void noItemCanBeDeletedOnceStudentsHaveAttempts() {
        lockedChoiceItem();

        assertThatThrownBy(() -> service.deleteItem(7L))
                .isInstanceOf(QuizInUseException.class)
                .hasMessageContaining("nu mai poți șterge");
        verify(itemRepository, never()).delete(any(QuizItem.class));
    }

    @Test
    void theScoringOfATakenItemCannotChange() {
        lockedChoiceItem();
        List<OptionRequest> same = List.of(new OptionRequest(0, "$4$", true), new OptionRequest(1, "$5$", false));

        assertThatThrownBy(() -> service.updateItem(7L, new ItemRequest(QuizItemType.SINGLE_CHOICE, 1, "Cât e $2+2$?", 5, null, same)))
                .as("points").isInstanceOf(QuizInUseException.class).hasMessageContaining("doar textele");
        assertThatThrownBy(() -> service.updateItem(7L, new ItemRequest(QuizItemType.SINGLE_CHOICE, 1, "Cât e $2+2$?", 2, null,
                List.of(new OptionRequest(0, "$4$", false), new OptionRequest(1, "$5$", true)))))
                .as("correct option").isInstanceOf(QuizInUseException.class);
        assertThatThrownBy(() -> service.updateItem(7L, new ItemRequest(QuizItemType.SINGLE_CHOICE, 1, "Cât e $2+2$?", 2, null,
                List.of(new OptionRequest(0, "$4$", true), new OptionRequest(1, "$5$", false), new OptionRequest(2, "$6$", false)))))
                .as("option count").isInstanceOf(QuizInUseException.class);
        verify(optionRepository, never()).deleteByItemId(any());
    }

    @Test
    void theTextsOfATakenItemCanStillBeCorrectedInPlace() {
        QuizItem item = lockedChoiceItem();
        when(hintRepository.save(any(QuizItemHint.class))).thenAnswer(i -> i.getArgument(0));

        ItemDto dto = service.updateItem(7L, new ItemRequest(QuizItemType.SINGLE_CHOICE, 1, "Cât face $2+2$?", 2, "$2+2=4$",
                List.of(new OptionRequest(0, "$4$ (patru)", true), new OptionRequest(1, "$5$", false)), List.of("Numără pe degete.")));

        assertThat(item.getStatement()).isEqualTo("Cât face $2+2$?");
        assertThat(dto.options()).extracting(QuizDtos.OptionDto::text).containsExactly("$4$ (patru)", "$5$");
        verify(optionRepository, never()).deleteByItemId(any()); // the students' chosen options must keep existing
    }

    // --- M4: an empty quiz cannot reach the students ---

    @Test
    void aQuizWithoutItemsCannotBePublished() {
        ReflectionTestUtils.setField(quiz, "id", 1L);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.countByQuizId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service.setPublished(1L, true))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("cel puțin un subiect");
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.DRAFT);
    }

    @Test
    void aQuizWithItemsIsPublished() {
        ReflectionTestUtils.setField(quiz, "id", 1L);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(itemRepository.countByQuizId(1L)).thenReturn(3L);

        service.setPublished(1L, true);

        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
    }

    @Test
    void theLastItemOfAPublishedQuizCannotBeDeleted() {
        ReflectionTestUtils.setField(quiz, "id", 1L);
        quiz.publish();
        QuizItem only = new QuizItem(quiz, 1, QuizItemType.OPEN, "Demonstrează", 3, null);
        when(itemRepository.findById(7L)).thenReturn(Optional.of(only));
        when(itemRepository.countByQuizId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteItem(7L))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("Depublică");
        verify(itemRepository, never()).delete(any(QuizItem.class));
    }

    // --- U9: copying a quiz ---

    @Test
    void aCopyIsADraftWithTheSameSettingsItemsOptionsAndHints() {
        ReflectionTestUtils.setField(quiz, "id", 1L);
        quiz.assignToClass(ninth);
        quiz.changeTimeLimit(30);
        quiz.allowPractice(true);
        quiz.publish();
        QuizItem item = new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 2, "$4$");
        ReflectionTestUtils.setField(item, "id", 7L);
        when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));
        when(itemRepository.findByQuizIdOrderByPosition(1L)).thenReturn(List.of(item));
        when(itemRepository.save(any(QuizItem.class))).thenAnswer(i -> i.getArgument(0));
        when(optionRepository.findByItemIdOrderByPosition(7L)).thenReturn(List.of(
                new QuizOption(item, 0, "$4$", true), new QuizOption(item, 1, "$5$", false)));
        when(hintRepository.findByItemIdOrderByPosition(7L)).thenReturn(List.of(new QuizItemHint(item, 1, "Numără")));

        Quiz copy = service.copyQuiz(1L);

        assertThat(copy).isNotSameAs(quiz);
        assertThat(copy.getTitle()).isEqualTo("Simulare EN (copie)");
        assertThat(copy.getStatus()).isEqualTo(QuizStatus.DRAFT);
        assertThat(copy.getSchoolClass()).isSameAs(ninth);
        assertThat(copy.getTimeLimitMinutes()).isEqualTo(30);
        assertThat(copy.isPracticeAllowed()).isTrue();
        ArgumentCaptor<QuizOption> options = ArgumentCaptor.forClass(QuizOption.class);
        verify(optionRepository, org.mockito.Mockito.times(2)).save(options.capture());
        assertThat(options.getAllValues()).extracting(QuizOption::getText, QuizOption::isCorrect)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("$4$", true), org.assertj.core.groups.Tuple.tuple("$5$", false));
        assertThat(options.getAllValues()).allSatisfy(o -> assertThat(o.getItem().getQuiz()).isSameAs(copy));
        verify(hintRepository).save(any(QuizItemHint.class));
    }

    @Test
    void aCopyOfAVeryLongTitleStillFitsTwoHundredCharacters() {
        Quiz longOne = new Quiz("x".repeat(200), null);
        when(quizRepository.findById(2L)).thenReturn(Optional.of(longOne));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(i -> i.getArgument(0));

        Quiz copy = service.copyQuiz(2L);

        assertThat(copy.getTitle()).hasSize(200).endsWith(" (copie)");
    }
}
