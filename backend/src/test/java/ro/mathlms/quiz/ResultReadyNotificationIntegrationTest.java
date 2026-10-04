package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.auth.EmailService;
import ro.mathlms.outbox.OutboxEvent;
import ro.mathlms.outbox.OutboxEventRepository;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxProcessor;
import ro.mathlms.outbox.OutboxStatus;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Phase 5.6 end to end on real Postgres: handing in a fully auto-graded quiz queues one email per recipient
 * in the outbox, in the same transaction as the grade; the processor then delivers them. A failing mail
 * server leaves the event PENDING for a retry instead of losing it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.notifications.result-ready.enabled=true", "app.outbox.base-backoff-seconds=0"})
class ResultReadyNotificationIntegrationTest {

    @MockitoBean private EmailService emailService;

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private OutboxProcessor processor;

    private record Submitted(Long attemptId, String studentEmail, String parentEmail) {
    }

    /** A student (with a linked parent) hands in a single-choice-only quiz -> graded at once. */
    private Submitted submitGradedQuiz() {
        String stamp = String.valueOf(System.nanoTime());
        User parent = userRepository.save(new User("parinte." + stamp + "@scoala.ro", "Maria Pop", Role.PARENT));
        User student = new User("elev." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT);
        student.linkParent(parent);
        student = userRepository.save(student);

        Quiz quiz = new Quiz("Notificare " + stamp, null);
        quiz.publish();
        quiz = quizRepository.save(quiz);
        QuizItem item = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", 5, null));
        QuizOption right = optionRepository.save(new QuizOption(item, 0, "A", true));
        optionRepository.save(new QuizOption(item, 1, "B", false));

        StartedAttemptDto started = attemptService.startAttempt(quiz.getId(), student.getEmail());
        attemptService.saveResponse(started.attemptId(), item.getId(), right.getId(), student.getEmail());
        attemptService.submit(started.attemptId(), student.getEmail());
        return new Submitted(started.attemptId(), student.getEmail(), parent.getEmail());
    }

    private List<OutboxEvent> eventsOf(Long attemptId) {
        return outboxRepository.findByEventTypeOrderById(OutboxEventTypes.RESULT_READY_EMAIL).stream()
                .filter(e -> e.getPayload().contains("\"attemptId\":" + attemptId + ","))
                .toList();
    }

    @Test
    void gradingQueuesOneEventPerRecipientAndTheProcessorMailsThem() {
        Submitted done = submitGradedQuiz();

        assertThat(eventsOf(done.attemptId())).hasSize(2).allSatisfy(e ->
                assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING));

        while (processor.processBatch() > 0) {
            // drain
        }

        verify(emailService).sendResultReadyToStudent(eq(done.studentEmail()), eq("Ana Pop"), any(), eq(5), eq(5), eq(done.attemptId()));
        verify(emailService).sendResultReadyToParent(eq(done.parentEmail()), eq("Ana Pop"), any(), eq(5), eq(5), any(), eq(done.attemptId()));
        assertThat(eventsOf(done.attemptId())).allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.DONE));
    }

    @Test
    void aMailServerOutageKeepsTheEventForARetry() {
        Submitted done = submitGradedQuiz();
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendResultReadyToStudent(eq(done.studentEmail()), any(), any(), anyInt(), anyInt(), any());

        processor.processBatch(); // one pass: the student's mail fails (PENDING + retry), the parent's goes out

        List<OutboxEvent> events = eventsOf(done.attemptId());
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(e.getAttempts()).isGreaterThanOrEqualTo(1);
            assertThat(e.getLastError()).contains("smtp down");
        });
        verify(emailService, times(1)).sendResultReadyToParent(eq(done.parentEmail()), any(), any(), anyInt(), anyInt(), any(), any());
    }
}
