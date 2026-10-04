package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The server-side quiz timer: the server's clock, never the browser's, decides whether an attempt is still open. */
class QuizAttemptTimerServiceTest {

    /** A clock the test moves by hand. */
    private static final class SettableClock extends Clock {
        Instant now = Instant.parse("2026-10-04T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final String EMAIL = "elev@scoala.ro";
    private static final Duration GRACE = QuizAttemptService.SUBMIT_GRACE;

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final ResultNotifier resultNotifier = mock(ResultNotifier.class);
    private final SettableClock clock = new SettableClock();
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository, mock(QuizItemHintRepository.class), attemptRepository, responseRepository,
            userRepository, mock(EnrollmentRepository.class), mock(AfterCommitCacheEvictor.class),
            resultNotifier, fileService, clock, "uploads/quiz-photos");

    private final User student = withId(new User(EMAIL, "Elev Pop", Role.STUDENT), 1L);
    private final Quiz timedQuiz = timed(withId(new Quiz("Simulare EN", null), 10L), 30);
    private final QuizItem choiceItem = withId(new QuizItem(timedQuiz, 1, QuizItemType.SINGLE_CHOICE, "s", 5, null), 100L);
    private final QuizOption correct = withId(new QuizOption(choiceItem, 0, "opt", true), 1000L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Quiz timed(Quiz quiz, Integer minutes) {
        quiz.publish();
        quiz.changeTimeLimit(minutes);
        return quiz;
    }

    private QuizAttempt timedAttempt(long id) {
        return withId(new QuizAttempt(timedQuiz, student), id);
    }

    private void stubAttempt(QuizAttempt attempt) {
        when(attemptRepository.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
    }

    private void stubQuizContent() {
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(choiceItem));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of(correct));
    }

    // --- answering ---

    @Test
    void anAnswerWithinTheDeadlineIsSaved() {
        QuizAttempt attempt = timedAttempt(50L);
        stubAttempt(attempt);
        when(itemRepository.findById(100L)).thenReturn(Optional.of(choiceItem));
        when(optionRepository.findById(1000L)).thenReturn(Optional.of(correct));
        when(responseRepository.findByAttemptIdAndItemId(50L, 100L)).thenReturn(Optional.empty());
        clock.now = attempt.getDeadlineAt().minusSeconds(1);

        service.saveResponse(50L, 100L, 1000L, EMAIL);

        verify(responseRepository).save(any(ItemResponse.class));
    }

    @Test
    void anAnswerWithinTheGracePeriodIsStillSaved() {
        QuizAttempt attempt = timedAttempt(50L);
        stubAttempt(attempt);
        when(itemRepository.findById(100L)).thenReturn(Optional.of(choiceItem));
        when(optionRepository.findById(1000L)).thenReturn(Optional.of(correct));
        when(responseRepository.findByAttemptIdAndItemId(50L, 100L)).thenReturn(Optional.empty());
        clock.now = attempt.getDeadlineAt().plus(GRACE); // exactly at the edge

        service.saveResponse(50L, 100L, 1000L, EMAIL);

        verify(responseRepository).save(any(ItemResponse.class));
    }

    @Test
    void anAnswerAfterTheDeadlineAndGraceIsRejectedAndNotSaved() {
        QuizAttempt attempt = timedAttempt(50L);
        stubAttempt(attempt);
        clock.now = attempt.getDeadlineAt().plus(GRACE).plusSeconds(1);

        assertThatThrownBy(() -> service.saveResponse(50L, 100L, 1000L, EMAIL))
                .isInstanceOf(AttemptExpiredException.class);

        verify(responseRepository, never()).save(any());
    }

    @Test
    void aPhotoAfterTheDeadlineAndGraceIsRejectedAndNotStored() throws Exception {
        QuizAttempt attempt = timedAttempt(50L);
        stubAttempt(attempt);
        clock.now = attempt.getDeadlineAt().plus(GRACE).plusSeconds(1);
        MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service.uploadOpenPhoto(50L, 101L, photo, EMAIL))
                .isInstanceOf(AttemptExpiredException.class);

        verify(fileService, never()).uploadImage(any(), any());
    }

    @Test
    void anUntimedAttemptNeverExpires() {
        Quiz untimed = timed(withId(new Quiz("Fara limita", null), 11L), null);
        QuizItem item = withId(new QuizItem(untimed, 1, QuizItemType.SINGLE_CHOICE, "s", 5, null), 110L);
        QuizOption option = withId(new QuizOption(item, 0, "opt", true), 1100L);
        QuizAttempt attempt = withId(new QuizAttempt(untimed, student), 51L);
        stubAttempt(attempt);
        when(itemRepository.findById(110L)).thenReturn(Optional.of(item));
        when(optionRepository.findById(1100L)).thenReturn(Optional.of(option));
        when(responseRepository.findByAttemptIdAndItemId(51L, 110L)).thenReturn(Optional.empty());
        clock.now = clock.now.plus(Duration.ofDays(365));

        service.saveResponse(51L, 110L, 1100L, EMAIL);

        verify(responseRepository).save(any(ItemResponse.class));
    }

    // --- submitting ---

    @Test
    void aLateSubmitStillHandsInWhatWasSavedBeforeTheDeadline() {
        QuizAttempt attempt = timedAttempt(50L);
        ItemResponse saved = new ItemResponse(attempt, choiceItem);
        saved.answerSingleChoice(correct);
        stubAttempt(attempt);
        stubQuizContent();
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(saved));
        clock.now = attempt.getDeadlineAt().plus(Duration.ofHours(2)); // long after: the saves were what counted

        AttemptResultDto result = service.submit(50L, EMAIL);

        assertThat(result.status()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(result.finalScore()).isEqualTo(5);
    }

    // --- starting / resuming ---

    @Test
    void startingATimedQuizReturnsTheDeadlineTheLimitAndTheServerClock() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(timedQuiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> withId(i.getArgument(0), 51L));
        stubQuizContent();

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL);

        assertThat(dto.quiz().timeLimitMinutes()).isEqualTo(30);
        assertThat(dto.deadlineAt()).isNotNull();
        assertThat(dto.serverNow()).isEqualTo(clock.now);
    }

    @Test
    void startingAnUntimedQuizHasNoDeadline() {
        Quiz untimed = timed(withId(new Quiz("Fara limita", null), 11L), null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(11L)).thenReturn(Optional.of(untimed));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(11L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> withId(i.getArgument(0), 52L));
        when(itemRepository.findByQuizIdOrderByPosition(11L)).thenReturn(List.of());

        StartedAttemptDto dto = service.startAttempt(11L, EMAIL);

        assertThat(dto.quiz().timeLimitMinutes()).isNull();
        assertThat(dto.deadlineAt()).isNull();
    }

    @Test
    void resumingAnAttemptThatIsStillOpenKeepsItsOriginalDeadline() {
        QuizAttempt existing = timedAttempt(50L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(timedQuiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.of(existing));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of());
        stubQuizContent();
        clock.now = existing.getDeadlineAt().minus(Duration.ofMinutes(5));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL);

        assertThat(dto.attemptId()).isEqualTo(50L);
        assertThat(dto.deadlineAt()).isEqualTo(existing.getDeadlineAt());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void resumingAnExpiredAttemptHandsItInFirstThenStartsAFreshOne() {
        QuizAttempt expired = timedAttempt(50L);
        ItemResponse saved = new ItemResponse(expired, choiceItem);
        saved.answerSingleChoice(correct);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(timedQuiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(10L, 1L, QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .thenReturn(Optional.of(expired));
        stubAttempt(expired);
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(saved));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> withId(i.getArgument(0), 51L));
        stubQuizContent();
        clock.now = expired.getDeadlineAt().plus(GRACE).plusSeconds(1);

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL);

        assertThat(expired.getStatus()).isEqualTo(QuizAttemptStatus.GRADED); // its saved answer was graded
        assertThat(expired.getScore()).isEqualTo(5);
        assertThat(dto.attemptId()).isEqualTo(51L);
        // the old row must reach the database BEFORE the new one is inserted (one in-progress attempt per quiz)
        InOrder order = inOrder(attemptRepository);
        order.verify(attemptRepository).saveAndFlush(expired);
        order.verify(attemptRepository).save(any(QuizAttempt.class));
    }

    // --- the expiry job's entry points ---

    @Test
    void overdueAttemptsAreFoundWithTheGraceSubtractedFromNow() {
        when(attemptRepository.findOverdueIds(clock.now.minus(GRACE))).thenReturn(List.of(50L, 60L));

        assertThat(service.findOverdueAttemptIds()).containsExactly(50L, 60L);
    }

    @Test
    void autoSubmitHandsInAnOverdueAttemptAndGradesWhatWasSaved() {
        QuizAttempt attempt = timedAttempt(50L);
        ItemResponse saved = new ItemResponse(attempt, choiceItem);
        saved.answerSingleChoice(correct);
        stubAttempt(attempt);
        stubQuizContent();
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(saved));
        clock.now = attempt.getDeadlineAt().plus(GRACE).plusSeconds(1);

        service.autoSubmitIfOverdue(50L);

        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(attempt.getScore()).isEqualTo(5);
        verify(attemptRepository).save(attempt);
        verify(resultNotifier).resultGraded(attempt);
    }

    @Test
    void autoSubmitLeavesAnAttemptAloneWhenItIsNotOverdueAnymore() {
        QuizAttempt attempt = timedAttempt(50L);
        stubAttempt(attempt);
        clock.now = attempt.getDeadlineAt().minusSeconds(5); // e.g. the student submitted/extended meanwhile

        service.autoSubmitIfOverdue(50L);

        assertThat(attempt.getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void autoSubmitIsANoOpForAnAttemptTheStudentAlreadyHandedIn() {
        QuizAttempt attempt = timedAttempt(50L);
        attempt.submit();
        stubAttempt(attempt);
        clock.now = attempt.getDeadlineAt().plus(Duration.ofHours(1));

        service.autoSubmitIfOverdue(50L);

        verify(attemptRepository, never()).save(any());
        verify(resultNotifier, never()).resultGraded(any());
    }

    @Test
    void autoSubmitIgnoresAnAttemptThatNoLongerExists() {
        when(attemptRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        service.autoSubmitIfOverdue(99L);

        verify(attemptRepository, never()).save(any());
        verify(responseRepository, never()).findByAttemptId(eq(99L));
    }
}
