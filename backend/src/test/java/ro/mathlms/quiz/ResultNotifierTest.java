package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.outbox.OutboxEventPublisher;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.quiz.ResultReadyPayload.Audience;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResultNotifierTest {

    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);

    private QuizAttempt attemptWithParent(boolean withParent) {
        User ana = new User("ana@scoala.ro", "Ana", Role.STUDENT);
        if (withParent) {
            ana.linkParent(new User("maria@scoala.ro", "Maria", Role.PARENT));
        }
        QuizAttempt attempt = new QuizAttempt(new Quiz("Simulare EN", null), ana);
        ReflectionTestUtils.setField(attempt, "id", 50L);
        return attempt;
    }

    @Test
    void whenDisabledNothingIsQueued() {
        new ResultNotifier(publisher, false).resultGraded(attemptWithParent(true));

        verify(publisher, never()).publish(any(), any());
    }

    @Test
    void aStudentWithoutAParentGetsOneEvent() {
        new ResultNotifier(publisher, true).resultGraded(attemptWithParent(false));

        verify(publisher).publish(OutboxEventTypes.RESULT_READY_EMAIL, new ResultReadyPayload(50L, Audience.STUDENT));
        verify(publisher, never()).publish(OutboxEventTypes.RESULT_READY_EMAIL, new ResultReadyPayload(50L, Audience.PARENT));
    }

    @Test
    void aStudentWithAParentGetsTwoSeparateEvents() {
        new ResultNotifier(publisher, true).resultGraded(attemptWithParent(true));

        verify(publisher).publish(OutboxEventTypes.RESULT_READY_EMAIL, new ResultReadyPayload(50L, Audience.STUDENT));
        verify(publisher).publish(OutboxEventTypes.RESULT_READY_EMAIL, new ResultReadyPayload(50L, Audience.PARENT));
    }
}
