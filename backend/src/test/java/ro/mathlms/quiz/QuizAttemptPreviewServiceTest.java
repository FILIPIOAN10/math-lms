package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.QuizPreviewDto;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the student sees before starting (the quiz preview) and the student's own access to the photos they uploaded.
 */
class QuizAttemptPreviewServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository,
            mock(QuizItemHintRepository.class), attemptRepository, responseRepository, userRepository,
            enrollmentRepository, mock(AfterCommitCacheEvictor.class), mock(ResultNotifier.class), fileService,
            Clock.systemUTC(), "uploads/quiz-photos");

    private static final String EMAIL = "elev@scoala.ro";

    private final User student = withId(new User(EMAIL, "Elev Pop", Role.STUDENT), 1L);
    private final User other = withId(new User("altul@scoala.ro", "Alt Elev", Role.STUDENT), 2L);
    private final Quiz quiz = withId(new Quiz("Simulare EN", "Recapitulare"), 10L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private QuizItem open(long id) {
        return withId(new QuizItem(quiz, 1, QuizItemType.OPEN, "Rezolvă", 4, null), id);
    }

    private void studentExists() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
    }

    // --- preview before starting ---

    @Test
    void previewTellsHowManyItemsAndPointsATimedQuizHas() {
        quiz.publish();
        quiz.changeTimeLimit(30);
        quiz.allowPractice(true);
        studentExists();
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(itemRepository.countByQuizId(10L)).thenReturn(3L);
        when(itemRepository.sumPointsByQuiz(List.of(10L))).thenReturn(List.of(new QuizMaxScore(10L, 11L)));

        QuizPreviewDto preview = service.getPreview(10L, EMAIL);

        assertThat(preview.id()).isEqualTo(10L);
        assertThat(preview.title()).isEqualTo("Simulare EN");
        assertThat(preview.description()).isEqualTo("Recapitulare");
        assertThat(preview.timeLimitMinutes()).isEqualTo(30);
        assertThat(preview.practiceAllowed()).isTrue();
        assertThat(preview.itemCount()).isEqualTo(3);
        assertThat(preview.maxScore()).isEqualTo(11);
    }

    @Test
    void previewOfAnUnpublishedQuizIsNotFound() {
        studentExists();
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz)); // still a draft

        assertThatThrownBy(() -> service.getPreview(10L, EMAIL)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void previewOfAnotherClasssQuizIsNotFoundRatherThanForbidden() {
        quiz.publish();
        quiz.assignToClass(withId(new SchoolClass("Clasa a 10-a", null), 5L));
        studentExists();
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(1L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getPreview(10L, EMAIL)).isInstanceOf(QuizNotFoundException.class);
    }

    // --- the student's own photo ---

    @Test
    void aStudentGetsBackThePhotoTheyUploaded() throws Exception {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, student), 50L);
        ItemResponse response = new ItemResponse(attempt, open(100L));
        response.answerOpen("stored.jpg");
        Resource image = new ByteArrayResource(new byte[] {1, 2, 3});
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(responseRepository.findByAttemptIdAndItemId(50L, 100L)).thenReturn(Optional.of(response));
        when(fileService.loadImage("uploads/quiz-photos", "stored.jpg")).thenReturn(image);

        assertThat(service.getOwnPhoto(50L, 100L, EMAIL)).isSameAs(image);
    }

    @Test
    void anotherStudentsPhotoIsNotFoundAndNeverRead() throws Exception {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, other), 50L);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.getOwnPhoto(50L, 100L, EMAIL)).isInstanceOf(QuizNotFoundException.class);
        verify(fileService, never()).loadImage(any(), any());
    }

    @Test
    void anItemWithoutAPhotoIsRejected() {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, student), 50L);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(responseRepository.findByAttemptIdAndItemId(50L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnPhoto(50L, 100L, EMAIL)).isInstanceOf(InvalidQuizException.class);
    }

    // --- the result names each item, so the page can show the photo next to it ---

    @Test
    void theResultCarriesEachItemsId() {
        quiz.publish();
        QuizAttempt attempt = withId(new QuizAttempt(quiz, student), 50L);
        attempt.submit();
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(open(100L)));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of());

        AttemptResultViewDto result = service.getResult(50L, EMAIL);

        assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.itemId()).isEqualTo(100L));
    }
}
