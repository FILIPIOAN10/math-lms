package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAttemptServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository,
            attemptRepository, responseRepository, userRepository);

    private static final String EMAIL = "elev@scoala.ro";

    private final User student = withId(new User(EMAIL, "Elev Pop", Role.STUDENT), 1L);
    private final Quiz quiz = published(withId(new Quiz("Simulare EN", null), 10L));

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Quiz published(Quiz quiz) {
        quiz.publish();
        return quiz;
    }

    private QuizItem singleChoice(long id, int points) {
        return withId(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", points, null), id);
    }

    private QuizItem open(long id, int points) {
        return withId(new QuizItem(quiz, 2, QuizItemType.OPEN, "s", points, null), id);
    }

    private QuizOption option(QuizItem item, long id, boolean correct) {
        return withId(new QuizOption(item, 0, "opt", correct), id);
    }

    private QuizAttempt attempt(long id, User owner) {
        return withId(new QuizAttempt(quiz, owner), id);
    }

    // --- startAttempt ---

    @Test
    void startRejectsUnpublishedQuiz() {
        Quiz draft = withId(new Quiz("Ciornă", null), 11L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(11L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.startAttempt(11L, EMAIL))
                .isInstanceOf(QuizNotFoundException.class);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void startResumesExistingInProgressAttempt() {
        QuizItem item = singleChoice(100L, 5);
        QuizOption a = option(item, 1000L, true);
        QuizAttempt existing = attempt(50L, student);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(10L, 1L, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existing));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(item));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of(a));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL);

        assertThat(dto.attemptId()).isEqualTo(50L);
        assertThat(dto.status()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        assertThat(dto.quiz().items()).hasSize(1);
        assertThat(dto.quiz().items().get(0).options()).extracting(o -> o.id()).containsExactly(1000L);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void startCreatesAttemptWhenNoneInProgress() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(10L, 1L, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> withId(i.getArgument(0), 51L));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of());

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL);

        assertThat(dto.attemptId()).isEqualTo(51L);
        verify(attemptRepository).save(any(QuizAttempt.class));
    }

    // --- saveResponse ---

    @Test
    void saveResponseRecordsTheChoice() {
        QuizItem item = singleChoice(100L, 5);
        QuizOption a = option(item, 1000L, true);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(optionRepository.findById(1000L)).thenReturn(Optional.of(a));
        when(responseRepository.findByAttemptIdAndItemId(50L, 100L)).thenReturn(Optional.empty());

        service.saveResponse(50L, 100L, 1000L, EMAIL);

        verify(responseRepository).save(any(ItemResponse.class));
    }

    @Test
    void saveResponseRejectsOtherStudentsAttempt() {
        User other = withId(new User("altul@scoala.ro", "Altul", Role.STUDENT), 2L);
        QuizAttempt attempt = attempt(50L, other);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.saveResponse(50L, 100L, 1000L, EMAIL))
                .isInstanceOf(QuizAccessException.class);
    }

    @Test
    void saveResponseRejectsSubmittedAttempt() {
        QuizAttempt attempt = attempt(50L, student);
        attempt.submit();
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.saveResponse(50L, 100L, 1000L, EMAIL))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("in progress");
    }

    @Test
    void saveResponseRejectsOpenItem() {
        QuizItem openItem = open(101L, 30);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(openItem));

        assertThatThrownBy(() -> service.saveResponse(50L, 101L, 1000L, EMAIL))
                .isInstanceOf(InvalidQuizException.class);
        verify(responseRepository, never()).save(any());
    }

    @Test
    void saveResponseRejectsOptionFromAnotherItem() {
        QuizItem item = singleChoice(100L, 5);
        QuizItem otherItem = singleChoice(102L, 5);
        QuizOption strayOption = option(otherItem, 1002L, true);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(optionRepository.findById(1002L)).thenReturn(Optional.of(strayOption));

        assertThatThrownBy(() -> service.saveResponse(50L, 100L, 1002L, EMAIL))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("does not belong");
    }

    // --- submit ---

    @Test
    void submitAutoGradesPureGrilaAndFinalises() {
        QuizItem item = singleChoice(100L, 5);
        QuizOption correct = option(item, 1000L, true);
        QuizAttempt attempt = attempt(50L, student);
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerSingleChoice(correct);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(item));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));

        AttemptResultDto result = service.submit(50L, EMAIL);

        assertThat(result.status()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(result.autoScore()).isEqualTo(5);
        assertThat(result.autoMaxScore()).isEqualTo(5);
        assertThat(result.finalScore()).isEqualTo(5);
        assertThat(response.getCorrect()).isTrue();
        assertThat(response.getAwardedPoints()).isEqualTo(5);
    }

    @Test
    void submitScoresWrongAndUnansweredAsZero() {
        QuizItem answered = singleChoice(100L, 5);
        QuizItem unanswered = singleChoice(101L, 7);
        QuizOption wrong = option(answered, 1001L, false);
        QuizAttempt attempt = attempt(50L, student);
        ItemResponse response = new ItemResponse(attempt, answered);
        response.answerSingleChoice(wrong);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(answered, unanswered));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));

        AttemptResultDto result = service.submit(50L, EMAIL);

        assertThat(result.status()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(result.autoScore()).isZero();
        assertThat(result.autoMaxScore()).isEqualTo(12);
        assertThat(result.finalScore()).isZero();
        assertThat(response.getCorrect()).isFalse();
        assertThat(response.getAwardedPoints()).isZero();
    }

    @Test
    void submitLeavesAttemptPendingWhenOpenItemsExist() {
        QuizItem grila = singleChoice(100L, 5);
        QuizItem deschis = open(101L, 30);
        QuizOption correct = option(grila, 1000L, true);
        QuizAttempt attempt = attempt(50L, student);
        ItemResponse response = new ItemResponse(attempt, grila);
        response.answerSingleChoice(correct);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(grila, deschis));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));

        AttemptResultDto result = service.submit(50L, EMAIL);

        assertThat(result.status()).isEqualTo(QuizAttemptStatus.SUBMITTED);
        assertThat(result.autoScore()).isEqualTo(5);
        assertThat(result.autoMaxScore()).isEqualTo(5);
        assertThat(result.finalScore()).isNull();
    }

    @Test
    void submitRejectsOtherStudentsAttempt() {
        User other = withId(new User("altul@scoala.ro", "Altul", Role.STUDENT), 2L);
        QuizAttempt attempt = attempt(50L, other);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.submit(50L, EMAIL))
                .isInstanceOf(QuizAccessException.class);
    }
}
