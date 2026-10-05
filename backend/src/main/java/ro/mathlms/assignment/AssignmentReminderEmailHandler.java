package ro.mathlms.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ro.mathlms.auth.EmailService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.outbox.OutboxEventTypes;
import ro.mathlms.outbox.OutboxHandler;
import ro.mathlms.outbox.OutboxPayloadCodec;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Sends one "homework due soon" email. Re-reads the current state at send time and stays silent when there is nothing to
 * tell any more: the assignment is gone, its deadline has passed, the student is erased or left the class, or has handed
 * in since the reminder was queued. A mail failure throws, so the dispatcher backs off and retries.
 */
@Component
public class AssignmentReminderEmailHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(AssignmentReminderEmailHandler.class);

    private final OutboxPayloadCodec codec;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptRepository attemptRepository;
    private final EmailService emailService;
    private final Clock clock;

    public AssignmentReminderEmailHandler(OutboxPayloadCodec codec, AssignmentRepository assignmentRepository,
                                          UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                                          QuizAttemptRepository attemptRepository, EmailService emailService, Clock clock) {
        this.codec = codec;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attemptRepository = attemptRepository;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.ASSIGNMENT_REMINDER_EMAIL;
    }

    @Override
    public void handle(String payload) {
        AssignmentReminderPayload event = codec.deserialize(payload, AssignmentReminderPayload.class);
        Assignment assignment = assignmentRepository.findByIdFetched(event.assignmentId()).orElse(null);
        User student = userRepository.findById(event.studentId()).orElse(null);
        if (assignment == null || student == null || student.isErased()
                || !enrollmentRepository.existsByStudentIdAndSchoolClassId(student.getId(), assignment.getSchoolClass().getId())
                || assignment.isOverdue(Instant.now(clock))) {
            log.info("Reminder for assignment {} / student {} skipped: nothing left to remind",
                    event.assignmentId(), event.studentId());
            return;
        }
        boolean done = AssignmentProgress.of(assignment, attemptRepository.findForAssignment(
                assignment.getQuiz().getId(), List.of(student.getId()), assignment.getCreatedAt())).isDone();
        if (done) {
            log.info("Reminder for assignment {} / student {} skipped: already handed in", event.assignmentId(), event.studentId());
            return;
        }
        emailService.sendAssignmentReminder(student.getEmail(), student.getFullName(), assignment.getQuiz().getTitle(),
                assignment.getDueAt(), assignment.getQuiz().getId());
    }
}
