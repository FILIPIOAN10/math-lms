package ro.mathlms.audit;

import org.springframework.data.domain.Limit;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditDtos.AuditEntryDto;
import ro.mathlms.audit.AuditDtos.AuditPageDto;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * The teachers' journal (F1): who changed what, and when. Services call {@link #record} next to the change it
 * describes. It joins the caller's transaction and refuses to run outside one ({@code MANDATORY}), so a change and its
 * row commit or roll back together: a change that failed is never in the journal, and one that happened always is.
 */
@Service
public class AuditLog {

    /** Days in the filter are school days: midnight to midnight in Romania, whatever the server's zone. */
    static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    static final int DEFAULT_PAGE = 50;
    static final int MAX_PAGE = 100;
    private static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("3000-01-01T00:00:00Z");

    private final AuditEntryRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AuditLog(AuditEntryRepository repository, UserRepository userRepository, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Records a change made by the signed-in user (the teacher). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        repository.save(new AuditEntry(clock.instant(), currentUserId(), event));
    }

    /** Records a change made by {@code actor} - for flows with nobody signed in yet (joining through an invite link). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(User actor, AuditEvent event) {
        repository.save(new AuditEntry(clock.instant(), actor == null ? null : actor.getId(), event));
    }

    /**
     * One page of the journal, newest first. Every filter is optional: {@code category}, {@code studentId}, and the
     * days {@code from}..{@code to} (both inclusive). {@code before} continues from a previous page's {@code nextBefore}.
     */
    @Transactional(readOnly = true)
    public AuditPageDto page(AuditCategory category, Long studentId, LocalDate from, LocalDate to, Long before,
                             Integer limit) {
        int size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, MAX_PAGE);
        List<AuditAction> actions = category == null ? Arrays.asList(AuditAction.values()) : AuditAction.in(category);
        // One extra row tells whether an older page exists without a count query.
        List<AuditRow> rows = repository.findPage(
                before == null ? Long.MAX_VALUE : before, actions,
                studentId == null, studentId == null ? -1L : studentId,
                from == null ? EARLIEST : from.atStartOfDay(ZONE).toInstant(),
                to == null ? LATEST : to.plusDays(1).atStartOfDay(ZONE).toInstant(),
                Limit.of(size + 1));
        boolean more = rows.size() > size;
        List<AuditEntryDto> entries = rows.stream().limit(size).map(AuditEntryDto::from).toList();
        return new AuditPageDto(entries, more ? entries.getLast().id() : null);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }
}
