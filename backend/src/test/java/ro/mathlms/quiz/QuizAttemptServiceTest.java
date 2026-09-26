package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.ItemResultDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

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

class QuizAttemptServiceTest {

    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final QuizAttemptService service = new QuizAttemptService(
            quizRepository, itemRepository, optionRepository,
            attemptRepository, responseRepository, userRepository,
            fileService, "uploads/quiz-photos");

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

    // --- uploadOpenPhoto ---

    private static MultipartFile image() {
        return new MockMultipartFile("file", "rezolvare.jpg", "image/jpeg", "bytes".getBytes());
    }

    @Test
    void uploadStoresPhotoAndSetsImageKey() throws Exception {
        QuizItem openItem = open(101L, 30);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(openItem));
        when(responseRepository.findByAttemptIdAndItemId(50L, 101L)).thenReturn(Optional.empty());
        when(fileService.uploadImage(eq("uploads/quiz-photos"), any())).thenReturn("stored.jpg");

        service.uploadOpenPhoto(50L, 101L, image(), EMAIL);

        ArgumentCaptor<ItemResponse> saved = ArgumentCaptor.forClass(ItemResponse.class);
        verify(responseRepository).save(saved.capture());
        assertThat(saved.getValue().getImageKey()).isEqualTo("stored.jpg");
    }

    @Test
    void uploadReplacesOldPhotoAndDeletesPrevious() throws Exception {
        QuizItem openItem = open(101L, 30);
        QuizAttempt attempt = attempt(50L, student);
        ItemResponse existing = new ItemResponse(attempt, openItem);
        existing.answerOpen("old.jpg");
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(openItem));
        when(responseRepository.findByAttemptIdAndItemId(50L, 101L)).thenReturn(Optional.of(existing));
        when(fileService.uploadImage(eq("uploads/quiz-photos"), any())).thenReturn("new.jpg");

        service.uploadOpenPhoto(50L, 101L, image(), EMAIL);

        assertThat(existing.getImageKey()).isEqualTo("new.jpg");
        verify(fileService).deleteImage("uploads/quiz-photos", "old.jpg");
    }

    @Test
    void uploadRejectsSingleChoiceItem() throws Exception {
        QuizItem grila = singleChoice(100L, 5);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(grila));

        assertThatThrownBy(() -> service.uploadOpenPhoto(50L, 100L, image(), EMAIL))
                .isInstanceOf(InvalidQuizException.class);
        verify(fileService, never()).uploadImage(any(), any());
    }

    @Test
    void uploadRejectsNonImageFile() throws Exception {
        QuizItem openItem = open(101L, 30);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(openItem));
        MultipartFile pdf = new MockMultipartFile("file", "x.pdf", "application/pdf", "bytes".getBytes());

        assertThatThrownBy(() -> service.uploadOpenPhoto(50L, 101L, pdf, EMAIL))
                .isInstanceOf(InvalidQuizException.class);
        verify(fileService, never()).uploadImage(any(), any());
    }

    @Test
    void uploadRejectsEmptyFile() throws Exception {
        QuizItem openItem = open(101L, 30);
        QuizAttempt attempt = attempt(50L, student);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(101L)).thenReturn(Optional.of(openItem));
        MultipartFile empty = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.uploadOpenPhoto(50L, 101L, empty, EMAIL))
                .isInstanceOf(InvalidQuizException.class);
        verify(fileService, never()).uploadImage(any(), any());
    }

    @Test
    void uploadRejectsOtherStudentsAttempt() {
        User other = withId(new User("altul@scoala.ro", "Altul", Role.STUDENT), 2L);
        QuizAttempt attempt = attempt(50L, other);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.uploadOpenPhoto(50L, 101L, image(), EMAIL))
                .isInstanceOf(QuizAccessException.class);
    }
    @Test
    void gradeOpenResponse_Success() {
        // Arrange
        QuizAttempt attempt = mock(QuizAttempt.class);
        when(attempt.getStatus()).thenReturn(QuizAttemptStatus.SUBMITTED);
        when(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt));

        QuizItem item = mock(QuizItem.class);
        when(item.getType()).thenReturn(QuizItemType.OPEN);
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item));

        ItemResponse response = mock(ItemResponse.class);
        when(responseRepository.findByAttemptIdAndItemId(1L, 2L)).thenReturn(Optional.of(response));

        // Act
        service.gradeOpenResponse(1L, 2L, 15);

        // Assert
        verify(response).gradeManual(15);
        verify(responseRepository).save(response);
    }

    // --- getResult (Q8) ---

    @Test
    void getResultRevealsCorrectAnswerBaremAndScore() {
        QuizItem grila = singleChoice(100L, 5);
        QuizOption correct = option(grila, 1000L, true);
        QuizItem deschis = withId(new QuizItem(quiz, 2, QuizItemType.OPEN, "Rezolvă", 30, "barem-x"), 101L);
        QuizAttempt attempt = attempt(50L, student);
        attempt.submit();
        attempt.markGraded(5);
        ItemResponse r1 = new ItemResponse(attempt, grila);
        r1.answerSingleChoice(correct);
        r1.gradeAuto(true, 5);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(grila, deschis));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(r1));
        when(optionRepository.findByItemIdOrderByPosition(100L)).thenReturn(List.of(correct));

        AttemptResultViewDto view = service.getResult(50L, EMAIL);

        assertThat(view.finalScore()).isEqualTo(5);
        assertThat(view.maxScore()).isEqualTo(35);
        assertThat(view.status()).isEqualTo(QuizAttemptStatus.GRADED);
        ItemResultDto grView = view.items().get(0);
        assertThat(grView.correctOptionText()).isEqualTo("opt");
        assertThat(grView.selectedOptionText()).isEqualTo("opt");
        assertThat(grView.correct()).isTrue();
        assertThat(grView.awardedPoints()).isEqualTo(5);
        ItemResultDto openView = view.items().get(1);
        assertThat(openView.barem()).isEqualTo("barem-x");
        assertThat(openView.photoUploaded()).isFalse();
    }

    @Test
    void getResultRejectsInProgressAttempt() {
        QuizAttempt attempt = attempt(50L, student); // still IN_PROGRESS
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.getResult(50L, EMAIL))
                .isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void getResultRejectsOtherStudentsAttempt() {
        User other = withId(new User("altul@scoala.ro", "Altul", Role.STUDENT), 2L);
        QuizAttempt attempt = attempt(50L, other);
        attempt.submit();
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.getResult(50L, EMAIL))
                .isInstanceOf(QuizAccessException.class);
    }

    @Test
    void gradeOpenResponseRejectsSingleChoiceItem() {
        QuizItem grila = singleChoice(100L, 5);
        QuizAttempt attempt = attempt(50L, student);
        attempt.submit();
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.findById(100L)).thenReturn(Optional.of(grila));

        assertThatThrownBy(() -> service.gradeOpenResponse(50L, 100L, 5))
                .isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void gradeOpenResponse_FailsIfNotSubmitted() {
        // Arrange
        QuizAttempt attempt = mock(QuizAttempt.class);
        when(attempt.getStatus()).thenReturn(QuizAttemptStatus.IN_PROGRESS); // Greșit pentru corectură
        when(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt));

        // Act & Assert
        assertThatThrownBy(() -> service.gradeOpenResponse(1L, 2L, 15))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("SUBMITTED");
    }

    @Test
    void finalizeGrading_Success() {
        // Arrange
        Quiz quiz = mock(Quiz.class);
        when(quiz.getId()).thenReturn(99L);

        QuizAttempt attempt = mock(QuizAttempt.class);
        when(attempt.getStatus()).thenReturn(QuizAttemptStatus.SUBMITTED);
        when(attempt.getQuiz()).thenReturn(quiz);
        when(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt));

        QuizItem item = mock(QuizItem.class);
        when(item.getId()).thenReturn(2L);
        when(item.getType()).thenReturn(QuizItemType.OPEN);
        when(itemRepository.findByQuizIdOrderByPosition(99L)).thenReturn(List.of(item));

        ItemResponse response = mock(ItemResponse.class);
        when(response.getItem()).thenReturn(item);
        when(response.getAwardedPoints()).thenReturn(15); // Corectat
        when(responseRepository.findByAttemptId(1L)).thenReturn(List.of(response));

        // Act
        service.finalizeGrading(1L);

        // Assert
        verify(attempt).markGraded(15);
        verify(attemptRepository).save(attempt);
    }

    @Test
    void finalizeGrading_FailsIfOpenItemNotGraded() {
        // Arrange
        Quiz quiz = mock(Quiz.class);
        when(quiz.getId()).thenReturn(99L);

        QuizAttempt attempt = mock(QuizAttempt.class);
        when(attempt.getStatus()).thenReturn(QuizAttemptStatus.SUBMITTED);
        when(attempt.getQuiz()).thenReturn(quiz);
        when(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt));

        QuizItem item = mock(QuizItem.class);
        when(item.getId()).thenReturn(2L);
        when(item.getType()).thenReturn(QuizItemType.OPEN);
        when(itemRepository.findByQuizIdOrderByPosition(99L)).thenReturn(List.of(item));

        ItemResponse response = mock(ItemResponse.class);
        when(response.getItem()).thenReturn(item);
        when(response.getAwardedPoints()).thenReturn(null); // NECorectat!
        when(responseRepository.findByAttemptId(1L)).thenReturn(List.of(response));

        // Act & Assert
        assertThatThrownBy(() -> service.finalizeGrading(1L))
                .isInstanceOf(InvalidQuizException.class)
                .hasMessageContaining("nu a fost corectat");
    }

    // --- Q9: resume restores saved answers + "my attempts" ---

    @Test
    void startReturnsSavedAnswersWhenResuming() {
        QuizItem grila = singleChoice(100L, 5);
        QuizOption chosen = option(grila, 1000L, false);
        QuizItem deschis = open(101L, 30);
        QuizAttempt existing = attempt(50L, student);
        ItemResponse r1 = new ItemResponse(existing, grila);
        r1.answerSingleChoice(chosen);
        ItemResponse r2 = new ItemResponse(existing, deschis);
        r2.answerOpen("poza.jpg");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findByQuizIdAndStudentIdAndStatus(10L, 1L, QuizAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existing));
        when(itemRepository.findByQuizIdOrderByPosition(10L)).thenReturn(List.of(grila, deschis));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(r1, r2));

        StartedAttemptDto dto = service.startAttempt(10L, EMAIL);

        assertThat(dto.answers()).hasSize(2);
        assertThat(dto.answers().get(0).itemId()).isEqualTo(100L);
        assertThat(dto.answers().get(0).selectedOptionId()).isEqualTo(1000L);
        assertThat(dto.answers().get(1).itemId()).isEqualTo(101L);
        assertThat(dto.answers().get(1).photoUploaded()).isTrue();
    }

    @Test
    void listMyAttemptsMapsTheStudentsAttempts() {
        QuizAttempt graded = attempt(50L, student);
        graded.submit();
        graded.markGraded(7);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        when(attemptRepository.findByStudentIdOrderByStartedAtDesc(1L)).thenReturn(List.of(graded));

        List<MyAttemptDto> mine = service.listMyAttempts(EMAIL);

        assertThat(mine).singleElement().satisfies(dto -> {
            assertThat(dto.attemptId()).isEqualTo(50L);
            assertThat(dto.quizTitle()).isEqualTo("Simulare EN");
            assertThat(dto.status()).isEqualTo(QuizAttemptStatus.GRADED);
            assertThat(dto.score()).isEqualTo(7);
            assertThat(dto.submittedAt()).isNotNull();
        });
    }
}
