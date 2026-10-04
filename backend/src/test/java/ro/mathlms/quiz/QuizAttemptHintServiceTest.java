package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.HintDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E3: progressive hints - practice only, one at a time and in order, free, and the usage is recorded. */
class QuizAttemptHintServiceTest {

    private static final String EMAIL = "elev@scoala.ro";

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizItemHintRepository hintRepository = mock(QuizItemHintRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository, hintRepository, attemptRepository, responseRepository,
            userRepository, mock(EnrollmentRepository.class), mock(AfterCommitCacheEvictor.class),
            mock(ResultNotifier.class), mock(FileService.class), Clock.systemUTC(), "uploads/quiz-photos");

    private final User student = withId(new User(EMAIL, "Elev Pop", Role.STUDENT), 1L);
    private final Quiz quiz = practiceQuiz();
    private final QuizItem item = withId(new QuizItem(quiz, 1, QuizItemType.OPEN, "s", 10, "barem"), 100L);
    private final QuizHintFixture hints = new QuizHintFixture(item);

    /** Three hints for the item, in order. */
    private static final class QuizHintFixture {
        final List<QuizItemHint> list;

        QuizHintFixture(QuizItem item) {
            list = List.of(new QuizItemHint(item, 1, "indiciul 1"), new QuizItemHint(item, 2, "indiciul 2"),
                    new QuizItemHint(item, 3, "indiciul 3"));
        }
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Quiz practiceQuiz() {
        Quiz q = withId(new Quiz("Simulare EN", null), 10L);
        q.publish();
        q.allowPractice(true);
        return q;
    }

    private QuizAttempt practice(long id) {
        return withId(new QuizAttempt(quiz, student, Instant.now(), AttemptMode.PRACTICE), id);
    }

    private QuizAttempt test(long id) {
        return withId(new QuizAttempt(quiz, student), id);
    }

    private void stubReveal(QuizAttempt attempt) {
        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(hintRepository.findByItemIdOrderByPosition(100L)).thenReturn(hints.list);
        when(responseRepository.findByAttemptIdAndItemId(attempt.getId(), 100L)).thenReturn(Optional.empty());
    }

    private ItemResponse responseWithHintsUsed(QuizAttempt attempt, int used) {
        ItemResponse response = new ItemResponse(attempt, item);
        for (int i = 1; i <= used; i++) {
            response.revealHint(i);
        }
        when(responseRepository.findByAttemptIdAndItemId(attempt.getId(), 100L)).thenReturn(Optional.of(response));
        return response;
    }

    // --- revealing ---

    @Test
    void theFirstHintIsRevealedAndRecordedEvenBeforeAnyAnswer() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);

        HintDto hint = service.revealHint(50L, 100L, 1, EMAIL);

        assertThat(hint).isEqualTo(new HintDto(1, "indiciul 1", 3));
        ArgumentCaptor<ItemResponse> saved = ArgumentCaptor.forClass(ItemResponse.class);
        verify(responseRepository).save(saved.capture());
        assertThat(saved.getValue().getHintsUsed()).isEqualTo(1);
        assertThat(saved.getValue().getSelectedOption()).isNull(); // a hint is not an answer
    }

    @Test
    void theNextHintFollowsTheOnesAlreadyRevealed() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);
        ItemResponse response = responseWithHintsUsed(attempt, 1);

        HintDto hint = service.revealHint(50L, 100L, 2, EMAIL);

        assertThat(hint).isEqualTo(new HintDto(2, "indiciul 2", 3));
        assertThat(response.getHintsUsed()).isEqualTo(2);
    }

    @Test
    void hintsCannotBeSkippedAhead() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);
        responseWithHintsUsed(attempt, 1);

        assertThatThrownBy(() -> service.revealHint(50L, 100L, 3, EMAIL))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("pe rând");

        verify(responseRepository, never()).save(any());
    }

    @Test
    void askingAgainForAnAlreadyRevealedHintJustReturnsItAndKeepsTheCount() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);
        ItemResponse response = responseWithHintsUsed(attempt, 2);

        HintDto again = service.revealHint(50L, 100L, 1, EMAIL); // a double click, or a reloaded page

        assertThat(again).isEqualTo(new HintDto(1, "indiciul 1", 3));
        assertThat(response.getHintsUsed()).isEqualTo(2);
    }

    @Test
    void aHintNumberOutsideTheItemsHintsIsRejected() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);
        responseWithHintsUsed(attempt, 3);

        assertThatThrownBy(() -> service.revealHint(50L, 100L, 4, EMAIL)).isInstanceOf(InvalidQuizException.class);
        assertThatThrownBy(() -> service.revealHint(50L, 100L, 0, EMAIL)).isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void anItemWithoutHintsHasNothingToReveal() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);
        when(hintRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.revealHint(50L, 100L, 1, EMAIL)).isInstanceOf(InvalidQuizException.class);
    }

    // --- who may ask ---

    @Test
    void aGradedTestNeverGivesHints() {
        QuizAttempt attempt = test(50L);
        stubReveal(attempt);

        assertThatThrownBy(() -> service.revealHint(50L, 100L, 1, EMAIL))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("practic");

        verify(responseRepository, never()).save(any());
    }

    @Test
    void anotherStudentsAttemptIsOffLimits() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);

        assertThatThrownBy(() -> service.revealHint(50L, 100L, 1, "alt.elev@scoala.ro"))
                .isInstanceOf(QuizAccessException.class);
    }

    @Test
    void aFinishedPracticeGivesNoMoreHints() {
        QuizAttempt attempt = practice(50L);
        attempt.submit();
        attempt.completePractice();
        stubReveal(attempt);

        assertThatThrownBy(() -> service.revealHint(50L, 100L, 1, EMAIL)).isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void anItemOfAnotherQuizIsRejected() {
        QuizAttempt attempt = practice(50L);
        stubReveal(attempt);
        Quiz other = withId(new Quiz("Alt quiz", null), 11L);
        QuizItem foreign = withId(new QuizItem(other, 1, QuizItemType.OPEN, "s", 5, null), 200L);
        when(itemRepository.findById(200L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.revealHint(50L, 200L, 1, EMAIL)).isInstanceOf(InvalidQuizException.class);
    }

    // --- what the student screen gets ---

    private void stubStart() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(item));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of());
        when(hintRepository.countsByQuizId(10L)).thenReturn(List.of(new HintCount(100L, 3L)));
        when(hintRepository.findByItemIdOrderByPosition(100L)).thenReturn(hints.list);
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> withId(i.getArgument(0), 60L));
    }

    @Test
    void aPracticeTellsTheScreenHowManyHintsEachItemHas() {
        stubStart();
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.PRACTICE))
                .thenReturn(Optional.empty());

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE);

        assertThat(dto.quiz().items().get(0).hintCount()).isEqualTo(3);
        assertThat(dto.revealedHints()).isEmpty();
    }

    @Test
    void aGradedTestHidesEvenTheExistenceOfHints() {
        stubStart();
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.empty());

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.TEST);

        assertThat(dto.quiz().items().get(0).hintCount()).isZero();
        assertThat(dto.revealedHints()).isEmpty();
        verify(hintRepository, never()).countsByQuizId(any());
    }

    @Test
    void resumingAPracticeRestoresTheHintsAlreadyRevealed() {
        stubStart();
        QuizAttempt open = practice(50L);
        ItemResponse response = new ItemResponse(open, item);
        response.revealHint(2);
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.PRACTICE))
                .thenReturn(Optional.of(open));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE);

        assertThat(dto.revealedHints()).singleElement().satisfies(r -> {
            assertThat(r.itemId()).isEqualTo(100L);
            assertThat(r.hints()).containsExactly("indiciul 1", "indiciul 2");
        });
    }

    @Test
    void thePracticeResultShowsHowManyHintsWereUsedOnEachItem() {
        QuizAttempt attempt = practice(50L);
        attempt.submit();
        attempt.completePractice();
        ItemResponse response = new ItemResponse(attempt, item);
        response.revealHint(2);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(item));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));
        when(hintRepository.countsByQuizId(10L)).thenReturn(List.of(new HintCount(100L, 3L)));

        AttemptResultViewDto view = service.getResult(50L, EMAIL);

        assertThat(view.items()).singleElement().satisfies(i -> {
            assertThat(i.hintsUsed()).isEqualTo(2);
            assertThat(i.hintsAvailable()).isEqualTo(3);
        });
    }

    @Test
    void aRowThatExistsOnlyBecauseOfAHintIsNeverReturnedAsAnAnswerWithItsFeedback() {
        stubStart();
        QuizAttempt open = practice(50L);
        ItemResponse hintOnly = new ItemResponse(open, item);
        hintOnly.revealHint(1); // asked for a hint, never answered
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.PRACTICE))
                .thenReturn(Optional.of(open));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(hintOnly));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE);

        assertThat(dto.answers()).isEmpty();              // no "answer", so no feedback carrying the barem
        assertThat(dto.revealedHints()).hasSize(1);       // but the revealed hint itself is restored
    }
}
