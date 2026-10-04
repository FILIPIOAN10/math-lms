package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E2 practice mode: immediate feedback, no timer, never graded - and nothing leaks into a TEST attempt. */
class QuizAttemptPracticeServiceTest {

    private static final String EMAIL = "elev@scoala.ro";

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final AfterCommitCacheEvictor cacheEvictor = mock(AfterCommitCacheEvictor.class);
    private final ResultNotifier resultNotifier = mock(ResultNotifier.class);
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository, mock(QuizItemHintRepository.class), attemptRepository, responseRepository,
            userRepository, mock(EnrollmentRepository.class), cacheEvictor, resultNotifier, fileService,
            Clock.systemUTC(), "uploads/quiz-photos");

    private final User student = withId(new User(EMAIL, "Elev Pop", Role.STUDENT), 1L);
    private final Quiz quiz = practiceQuiz();
    private final QuizItem choiceItem = withId(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", 5, "barem grila"), 100L);
    private final QuizItem openItem = withId(new QuizItem(quiz, 2, QuizItemType.OPEN, "s", 10, "barem deschis"), 101L);
    private final QuizOption right = withId(new QuizOption(choiceItem, 0, "A", true), 1000L);
    private final QuizOption wrong = withId(new QuizOption(choiceItem, 1, "B", false), 1001L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Quiz practiceQuiz() {
        Quiz q = withId(new Quiz("Simulare EN", null), 10L);
        q.publish();
        q.changeTimeLimit(30);   // timed - a practice must still have no clock
        q.allowPractice(true);
        return q;
    }

    private QuizAttempt practiceAttempt(long id) {
        return withId(new QuizAttempt(quiz, student, java.time.Instant.now(), AttemptMode.PRACTICE), id);
    }

    private QuizAttempt testAttempt(long id) {
        return withId(new QuizAttempt(quiz, student), id);
    }

    private void stubStart() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(choiceItem, openItem));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of(right, wrong));
        when(optionRepository.findByItemIdOrderByPosition(101L)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> withId(i.getArgument(0), 60L));
    }

    private void stubAnswering(QuizAttempt attempt) {
        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(choiceItem));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(openItem));
        when(optionRepository.findById(1000L)).thenReturn(Optional.of(right));
        when(optionRepository.findById(1001L)).thenReturn(Optional.of(wrong));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of(right, wrong));
        when(responseRepository.findByAttemptIdAndItemId(eq(attempt.getId()), any())).thenReturn(Optional.empty());
    }

    // --- starting ---

    @Test
    void practiceIsRefusedWhenTheTeacherHasNotAllowedItForThisQuiz() {
        quiz.allowPractice(false);
        stubStart();

        assertThatThrownBy(() -> service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("practic");

        verify(attemptRepository, never()).save(any());
    }

    @Test
    void aPracticeAttemptHasNoDeadlineEvenThoughTheQuizIsTimed() {
        stubStart();
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.PRACTICE))
                .thenReturn(Optional.empty());

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE);

        ArgumentCaptor<QuizAttempt> saved = ArgumentCaptor.forClass(QuizAttempt.class);
        verify(attemptRepository).save(saved.capture());
        assertThat(saved.getValue().getMode()).isEqualTo(AttemptMode.PRACTICE);
        assertThat(saved.getValue().getDeadlineAt()).isNull();
        assertThat(dto.mode()).isEqualTo(AttemptMode.PRACTICE);
        assertThat(dto.deadlineAt()).isNull();
    }

    @Test
    void startingPracticeResumesTheOpenPracticeNotTheOpenTest() {
        stubStart();
        QuizAttempt openPractice = practiceAttempt(50L);
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.PRACTICE))
                .thenReturn(Optional.of(openPractice));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of());

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE);

        assertThat(dto.attemptId()).isEqualTo(50L);
        verify(attemptRepository, never()).save(any());
        verify(attemptRepository, never()).findByQuizIdAndStudentIdAndStatusAndMode(any(), any(), any(), eq(AttemptMode.TEST));
    }

    @Test
    void startingATestIgnoresAnOpenPractice() {
        stubStart();
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.empty());

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.TEST);

        ArgumentCaptor<QuizAttempt> saved = ArgumentCaptor.forClass(QuizAttempt.class);
        verify(attemptRepository).save(saved.capture());
        assertThat(saved.getValue().getMode()).isEqualTo(AttemptMode.TEST);
        assertThat(saved.getValue().getDeadlineAt()).isNotNull();
        assertThat(dto.mode()).isEqualTo(AttemptMode.TEST);
    }

    @Test
    void aResumedPracticeRestoresItsAnswersTogetherWithTheirFeedback() {
        stubStart();
        QuizAttempt openPractice = practiceAttempt(50L);
        ItemResponse earlier = new ItemResponse(openPractice, choiceItem);
        earlier.answerSingleChoice(wrong);
        earlier.gradeAuto(false, 0);
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.PRACTICE))
                .thenReturn(Optional.of(openPractice));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(earlier));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.PRACTICE);

        assertThat(dto.answers()).hasSize(1);
        assertThat(dto.answers().get(0).feedback()).isEqualTo(new AnswerFeedbackDto(false, 1000L, "barem grila"));
    }

    @Test
    void aResumedTestNeverCarriesFeedback() {
        stubStart();
        QuizAttempt openTest = testAttempt(50L);
        ItemResponse earlier = new ItemResponse(openTest, choiceItem);
        earlier.answerSingleChoice(right);
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.of(openTest));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(earlier));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL, AttemptMode.TEST);

        assertThat(dto.answers().get(0).feedback()).isNull();
    }

    // --- answering ---

    @Test
    void aCorrectPracticeAnswerGetsImmediateFeedbackAndIsMarkedOnTheResponse() {
        QuizAttempt attempt = practiceAttempt(50L);
        stubAnswering(attempt);

        Optional<AnswerFeedbackDto> feedback = service.saveResponse(50L, 100L, 1000L, EMAIL);

        assertThat(feedback).contains(new AnswerFeedbackDto(true, 1000L, "barem grila"));
        ArgumentCaptor<ItemResponse> saved = ArgumentCaptor.forClass(ItemResponse.class);
        verify(responseRepository).save(saved.capture());
        assertThat(saved.getValue().getCorrect()).isTrue();
        assertThat(saved.getValue().getAwardedPoints()).isEqualTo(5);
    }

    @Test
    void aWrongPracticeAnswerShowsWhichOptionWasRight() {
        QuizAttempt attempt = practiceAttempt(50L);
        stubAnswering(attempt);

        Optional<AnswerFeedbackDto> feedback = service.saveResponse(50L, 100L, 1001L, EMAIL);

        assertThat(feedback).contains(new AnswerFeedbackDto(false, 1000L, "barem grila"));
    }

    @Test
    void aTestAnswerNeverReturnsFeedback() {
        QuizAttempt attempt = testAttempt(50L);
        stubAnswering(attempt);

        Optional<AnswerFeedbackDto> feedback = service.saveResponse(50L, 100L, 1000L, EMAIL);

        assertThat(feedback).isEmpty(); // the anti-cheat rule of the graded test is unchanged
        ArgumentCaptor<ItemResponse> saved = ArgumentCaptor.forClass(ItemResponse.class);
        verify(responseRepository).save(saved.capture());
        assertThat(saved.getValue().getCorrect()).isNull(); // and nothing is graded until submit
    }

    @Test
    void aPracticePhotoGetsTheBaremToCompareWith() throws Exception {
        QuizAttempt attempt = practiceAttempt(50L);
        stubAnswering(attempt);
        when(fileService.uploadImage(eq("uploads/quiz-photos"), any())).thenReturn("stored.jpg");
        MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1});

        Optional<AnswerFeedbackDto> feedback = service.uploadOpenPhoto(50L, 101L, photo, EMAIL);

        assertThat(feedback).contains(new AnswerFeedbackDto(null, null, "barem deschis")); // open: no auto-verdict
    }

    @Test
    void aTestPhotoNeverReturnsTheBarem() throws Exception {
        QuizAttempt attempt = testAttempt(50L);
        stubAnswering(attempt);
        when(fileService.uploadImage(eq("uploads/quiz-photos"), any())).thenReturn("stored.jpg");
        MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1});

        assertThat(service.uploadOpenPhoto(50L, 101L, photo, EMAIL)).isEmpty();
    }

    // --- finishing ---

    @Test
    void finishingAPracticeEndsItWithoutAScoreAndTouchesNoCacheOrEmail() {
        QuizAttempt attempt = practiceAttempt(50L);
        ItemResponse answered = new ItemResponse(attempt, choiceItem);
        answered.answerSingleChoice(right);
        when(attemptRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(choiceItem, openItem));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(answered));

        AttemptResultDto result = service.submit(50L, EMAIL);

        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(attempt.getScore()).isNull();
        assertThat(result.finalScore()).isNull();
        verify(cacheEvictor, never()).evict(any(), any());      // progress / stats are unaffected
        verify(resultNotifier, never()).resultGraded(any());    // nobody is emailed about a practice
    }

    // --- reading back ---

    @Test
    void theResultOfAPracticeSaysItIsAPractice() {
        QuizAttempt attempt = practiceAttempt(50L);
        attempt.submit();
        attempt.completePractice();
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(choiceItem));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of(right, wrong));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of());

        AttemptResultViewDto view = service.getResult(50L, EMAIL);

        assertThat(view.mode()).isEqualTo(AttemptMode.PRACTICE);
        assertThat(view.finalScore()).isNull();
    }

    @Test
    void theProgressChartCountsOnlyGradedTests() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(attemptRepository.findByStudentIdAndStatusAndModeOrderBySubmittedAtAsc(
                1L, QuizAttemptStatus.GRADED, AttemptMode.TEST)).thenReturn(List.of());

        assertThat(service.getProgress(EMAIL)).isEmpty();

        verify(attemptRepository).findByStudentIdAndStatusAndModeOrderBySubmittedAtAsc(
                1L, QuizAttemptStatus.GRADED, AttemptMode.TEST);
    }

    @Test
    void myAttemptsShowTheModeOfEachSitting() {
        QuizAttempt practice = practiceAttempt(50L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(attemptRepository.findByStudentIdOrderByStartedAtDesc(1L)).thenReturn(List.of(practice));

        assertThat(service.listMyAttempts(EMAIL)).singleElement()
                .satisfies(a -> assertThat(a.mode()).isEqualTo(AttemptMode.PRACTICE));
    }
}
