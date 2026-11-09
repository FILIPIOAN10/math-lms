package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.user.User;

import java.time.Clock;
import java.time.Instant;

/**
 * "+1 încercare": one more graded attempt for one student at one test (Moodle's user override), so a dropped connection
 * or a wrong photo does not force raising the limit for the whole class. Given from one of the student's papers, which
 * names both the test and the student - whoever has used up their attempts has one.
 */
@Service
public class AttemptGrantService {

    private final QuizAttemptRepository attemptRepository;
    private final AttemptGrantRepository grantRepository;
    private final AuditLog auditLog;
    private final Clock clock;

    public AttemptGrantService(QuizAttemptRepository attemptRepository, AttemptGrantRepository grantRepository,
                               AuditLog auditLog, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.grantRepository = grantRepository;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Adds one attempt for the student of this paper at its test and answers with how many they may now hand in. A test
     * without a limit needs none (400); at most {@link AttemptGrant#MAX_EXTRA} in all. One journal row each time.
     */
    @Transactional
    public int grantOne(Long attemptId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        Quiz quiz = attempt.getQuiz();
        User student = attempt.getStudent();
        if (quiz.getMaxAttempts() == null) {
            throw new InvalidQuizException("Testul nu are limită de încercări: elevul îl poate da din nou oricum.");
        }
        AttemptGrant grant = grantRepository.findOne(quiz.getId(), student.getId()).orElse(null);
        int before = AttemptGrant.allowed(quiz.getMaxAttempts(), grant == null ? 0 : grant.getExtra());
        if (grant == null) {
            grant = new AttemptGrant(quiz, student, Instant.now(clock));
        } else {
            grant.addOne();
        }
        grantRepository.save(grant);
        int after = AttemptGrant.allowed(quiz.getMaxAttempts(), grant.getExtra());
        // The paper it was given from, like every grading row: the journal links "Vezi lucrarea" to it.
        auditLog.record(AuditEvent.of(AuditAction.ATTEMPT_GRANTED, attemptId, quiz.getTitle()).about(student)
                .details("încercări " + before + " → " + after));
        return after;
    }
}
