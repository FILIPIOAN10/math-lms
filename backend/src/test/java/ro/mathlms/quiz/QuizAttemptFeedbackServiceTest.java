package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptDetailDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptSummaryDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Results that say more than a bare number: every attempt list carries the quiz's maximum (so screens can show
 * "8 / 11"), and the teacher's written comments - per open answer and for the whole paper - reach the result.
 */
class QuizAttemptFeedbackServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository,
            mock(QuizItemHintRepository.class), attemptRepository, responseRepository, userRepository,
            mock(EnrollmentRepository.class), mock(AfterCommitCacheEvictor.class), mock(ResultNotifier.class),
            mock(FileService.class), Clock.systemUTC(), "uploads/quiz-photos");

    private static final String EMAIL = "elev@scoala.ro";

    private final User student = withId(new User(EMAIL, "Elev Pop", Role.STUDENT), 1L);
    private final Quiz quiz = withId(new Quiz("Simulare EN", null), 10L);
    private final Quiz other = withId(new Quiz("Teza", null), 11L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private QuizItem open(long id) {
        return withId(new QuizItem(quiz, 1, QuizItemType.OPEN, "Rezolvă", 10, null), id);
    }

    private QuizAttempt submitted(long id, Quiz forQuiz) {
        QuizAttempt attempt = withId(new QuizAttempt(forQuiz, student), id);
        attempt.submit();
        return attempt;
    }

    // --- "x / max" everywhere ---

    @Test
    void theStudentsAttemptsCarryEachQuizsMaximumFromOneQuery() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(attemptRepository.findByStudentIdOrderByStartedAtDesc(1L))
                .thenReturn(List.of(submitted(50L, quiz), submitted(51L, other), submitted(52L, quiz)));
        when(itemRepository.sumPointsByQuiz(anyCollection()))
                .thenReturn(List.of(new QuizMaxScore(10L, 11L), new QuizMaxScore(11L, 30L)));

        List<MyAttemptDto> mine = service.listMyAttempts(EMAIL);

        assertThat(mine).extracting(MyAttemptDto::maxScore).containsExactly(11, 30, 11);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(itemRepository).sumPointsByQuiz(ids.capture());
        assertThat(Set.copyOf(ids.getValue())).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void aQuizWithNoItemsHasMaximumZero() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(attemptRepository.findByStudentIdOrderByStartedAtDesc(1L)).thenReturn(List.of(submitted(50L, quiz)));
        when(itemRepository.sumPointsByQuiz(anyCollection())).thenReturn(List.of());

        assertThat(service.listMyAttempts(EMAIL)).singleElement()
                .satisfies(a -> assertThat(a.maxScore()).isZero());
    }

    @Test
    void theGradingQueueCarriesTheMaximumToo() {
        when(attemptRepository.findByStatusForGrading(QuizAttemptStatus.SUBMITTED)).thenReturn(List.of(submitted(50L, quiz)));
        when(itemRepository.sumPointsByQuiz(anyCollection())).thenReturn(List.of(new QuizMaxScore(10L, 11L)));

        List<AdminAttemptSummaryDto> queue = service.listForGrading(QuizAttemptStatus.SUBMITTED);

        assertThat(queue).singleElement().satisfies(a -> assertThat(a.maxScore()).isEqualTo(11));
    }

    // --- the teacher's comments ---

    @Test
    void gradingAnOpenAnswerKeepsTheComment() {
        QuizItem item = open(100L);
        QuizAttempt attempt = submitted(50L, quiz);
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerOpen("p.jpg");
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(item));
        when(responseRepository.findByAttemptIdAndItemId(50L, 100L)).thenReturn(Optional.of(response));

        service.gradeOpenResponse(50L, 100L, 8, "Ai uitat semnul la final.");

        assertThat(response.getAwardedPoints()).isEqualTo(8);
        assertThat(response.getTeacherComment()).isEqualTo("Ai uitat semnul la final.");
        verify(responseRepository).save(response);
    }

    @Test
    void anOverallCommentIsSavedOnTheAttempt() {
        QuizAttempt attempt = submitted(50L, quiz);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        service.commentOnAttempt(50L, "Lucrare foarte bună.");

        assertThat(attempt.getTeacherComment()).isEqualTo("Lucrare foarte bună.");
        verify(attemptRepository).save(attempt);
    }

    @Test
    void commentingOnAPaperStillBeingWrittenIsRejected() {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, student), 50L);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.commentOnAttempt(50L, "Prea devreme"))
                .isInstanceOf(InvalidQuizException.class);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void commentingOnAnUnknownAttemptIsNotFound() {
        when(attemptRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commentOnAttempt(99L, "?")).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void theResultShowsBothCommentsToTheStudent() {
        QuizItem item = open(100L);
        QuizAttempt attempt = submitted(50L, quiz);
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerOpen("p.jpg");
        response.gradeManual(8, "Ai uitat semnul.");
        attempt.commentOverall("Bravo!");
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(item));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));

        AttemptResultViewDto result = service.getResult(50L, EMAIL);

        assertThat(result.teacherComment()).isEqualTo("Bravo!");
        assertThat(result.items()).singleElement()
                .satisfies(i -> assertThat(i.teacherComment()).isEqualTo("Ai uitat semnul."));
    }

    @Test
    void theGradingScreenShowsTheCommentsAlreadyWritten() {
        QuizItem item = open(100L);
        QuizAttempt attempt = submitted(50L, quiz);
        ItemResponse response = new ItemResponse(attempt, item);
        response.gradeManual(8, "Ai uitat semnul.");
        attempt.commentOverall("Bravo!");
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(item));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(response));

        AdminAttemptDetailDto detail = service.getAttemptForGrading(50L);

        assertThat(detail.teacherComment()).isEqualTo("Bravo!");
        assertThat(detail.items()).singleElement()
                .satisfies(i -> assertThat(i.teacherComment()).isEqualTo("Ai uitat semnul."));
    }
}
