package ro.mathlms.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.util.Locale;

/**
 * Records security outcomes independently of the request's business transaction. It never stores passwords, MFA
 * codes, recovery codes, or email addresses. Known accounts are linked by their internal id; an unknown login has no
 * actor. A journal outage is logged but never changes the outcome of the authentication or authorization request.
 */
@Service
public class SecurityAuditLog {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLog.class);

    private final AuditEntryRepository entries;
    private final UserRepository users;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SecurityAuditLog(AuditEntryRepository entries, UserRepository users, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.entries = entries;
        this.users = users;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(AuditAction action, Long actorId, String details) {
        write(action, actorId, details, null);
    }

    /** Resolves the attempted account only to an internal id; the submitted address is never persisted. */
    public void recordForEmail(AuditAction action, String email, String details) {
        write(action, null, details, email == null ? null : email.strip().toLowerCase(Locale.ROOT));
    }

    private void write(AuditAction action, Long actorId, String details, String email) {
        try {
            transaction.executeWithoutResult(status -> {
                Long resolvedActor = actorId;
                if (resolvedActor == null && email != null) {
                    resolvedActor = users.findByEmail(email).map(User::getId).orElse(null);
                }
                AuditEvent event = AuditEvent.of(action, null, "—").details(details);
                entries.save(new AuditEntry(clock.instant(), resolvedActor, event));
            });
        } catch (RuntimeException e) {
            log.warn("Could not record security audit event {} ({})", action, e.getClass().getSimpleName());
        }
    }
}
