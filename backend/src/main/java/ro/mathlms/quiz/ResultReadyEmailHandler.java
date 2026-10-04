package ro.mathlms.quiz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ro.mathlms.auth.EmailService;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxHandler;
import ro.mathlms.outbox.OutboxPayloadCodec;
import ro.mathlms.user.User;

import java.util.List;

/**
 * Sends one "result is ready" email. Runs inside the outbox processor's transaction, so the lazy student and
 * parent links can be read. Idempotent in the way that matters: nothing is written, and a recipient is skipped
 * when there is nothing to tell (attempt gone, not graded, no parent, erased account). A mail failure throws,
 * so the dispatcher backs off and retries.
 */
@Component
public class ResultReadyEmailHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(ResultReadyEmailHandler.class);

    private final OutboxPayloadCodec codec;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final EmailService emailService;

    public ResultReadyEmailHandler(OutboxPayloadCodec codec, QuizAttemptRepository attemptRepository,
                                   QuizItemRepository itemRepository, EmailService emailService) {
        this.codec = codec;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.emailService = emailService;
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.RESULT_READY_EMAIL;
    }

    @Override
    public void handle(String payload) {
        ResultReadyPayload event = codec.deserialize(payload, ResultReadyPayload.class);
        QuizAttempt attempt = attemptRepository.findById(event.attemptId()).orElse(null);
        if (attempt == null || attempt.getStatus() != QuizAttemptStatus.GRADED) {
            log.info("Result email for attempt {} skipped: attempt missing or not graded", event.attemptId());
            return;
        }
        User student = attempt.getStudent();
        int maxScore = itemRepository.sumPointsByQuiz(List.of(attempt.getQuiz().getId())).stream()
                .findFirst().map(total -> total.maxScore().intValue()).orElse(0);
        int score = attempt.getScore() == null ? 0 : attempt.getScore();
        String quizTitle = attempt.getQuiz().getTitle();

        switch (event.audience()) {
            case STUDENT -> {
                if (!student.isErased()) {
                    emailService.sendResultReadyToStudent(student.getEmail(), student.getFullName(),
                            quizTitle, score, maxScore, attempt.getId());
                }
            }
            case PARENT -> {
                User parent = student.getParent();
                if (parent != null && !parent.isErased() && !student.isErased()) {
                    emailService.sendResultReadyToParent(parent.getEmail(), student.getFullName(),
                            quizTitle, score, maxScore, student.getId(), attempt.getId());
                }
            }
        }
    }
}
