package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.auth.EmailService;
import ro.mathlms.outbox.OutboxPayloadCodec;
import ro.mathlms.quiz.ResultReadyPayload.Audience;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultReadyEmailHandlerTest {

    private final OutboxPayloadCodec codec = new OutboxPayloadCodec();
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final QuizItemRepository itemRepository = mock(QuizItemRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final ResultReadyEmailHandler handler =
            new ResultReadyEmailHandler(codec, attemptRepository, itemRepository, emailService);

    private final User maria = withId(new User("maria@scoala.ro", "Maria Pop", Role.PARENT), 3L);
    private final User ana = withId(new User("ana@scoala.ro", "Ana Pop", Role.STUDENT), 2L);
    private final Quiz quiz = withId(new Quiz("Simulare EN", null), 10L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private QuizAttempt gradedAttempt(int score) {
        QuizAttempt attempt = withId(new QuizAttempt(quiz, ana), 50L);
        attempt.submit();
        attempt.markGraded(score);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(itemRepository.sumPointsByQuiz(List.of(10L))).thenReturn(List.of(new QuizMaxScore(10L, 15L)));
        return attempt;
    }

    private String payload(Audience audience) {
        return codec.serialize(new ResultReadyPayload(50L, audience));
    }

    @Test
    void theStudentGetsTheirScoreAndTheResultLink() {
        gradedAttempt(13);

        handler.handle(payload(Audience.STUDENT));

        verify(emailService).sendResultReadyToStudent("ana@scoala.ro", "Ana Pop", "Simulare EN", 13, 15, 50L);
        verify(emailService, never()).sendResultReadyToParent(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void theLinkedParentGetsTheirChildsScore() {
        gradedAttempt(13);
        ana.linkParent(maria);

        handler.handle(payload(Audience.PARENT));

        verify(emailService).sendResultReadyToParent("maria@scoala.ro", "Ana Pop", "Simulare EN", 13, 15, 2L, 50L);
        verify(emailService, never()).sendResultReadyToStudent(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void aParentEventWithoutALinkedParentSendsNothing() {
        gradedAttempt(13); // no parent linked (e.g. unlinked after the event was queued)

        handler.handle(payload(Audience.PARENT));

        verify(emailService, never()).sendResultReadyToParent(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void anErasedStudentIsNeverMailed() {
        gradedAttempt(13);
        ReflectionTestUtils.setField(ana, "erased", true);

        handler.handle(payload(Audience.STUDENT));

        verify(emailService, never()).sendResultReadyToStudent(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void aMissingOrUngradedAttemptIsSkippedNotRetriedForever() {
        when(attemptRepository.findById(50L)).thenReturn(Optional.empty());
        handler.handle(payload(Audience.STUDENT)); // must not throw

        QuizAttempt inProgress = withId(new QuizAttempt(quiz, ana), 50L);
        when(attemptRepository.findById(50L)).thenReturn(Optional.of(inProgress));
        handler.handle(payload(Audience.STUDENT)); // still must not throw

        verify(emailService, never()).sendResultReadyToStudent(anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void aMailFailureIsRethrownSoTheOutboxRetries() {
        gradedAttempt(13);
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendResultReadyToStudent(any(), any(), any(), anyInt(), anyInt(), any());

        assertThatThrownBy(() -> handler.handle(payload(Audience.STUDENT)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("smtp down");
    }

    @Test
    void aQuizWithoutItemsReportsZeroMax() {
        QuizAttempt attempt = gradedAttempt(0);
        when(itemRepository.sumPointsByQuiz(List.of(10L))).thenReturn(List.of());

        handler.handle(payload(Audience.STUDENT));

        verify(emailService).sendResultReadyToStudent("ana@scoala.ro", "Ana Pop", "Simulare EN", 0, 0, attempt.getId());
        assertThat(attempt.getScore()).isZero();
    }
}
