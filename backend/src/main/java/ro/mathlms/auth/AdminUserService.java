package ro.mathlms.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

/** Admin onboarding actions: list pending accounts, approve/reject, link a parent (decisions are journalled). */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditLog auditLog;

    public AdminUserService(UserRepository userRepository, AuditLog auditLog) {
        this.userRepository = userRepository;
        this.auditLog = auditLog;
    }

    /** Accounts that passed email verification and await an admin decision. */
    public List<User> listPending() {
        return userRepository.findByStatus(AccountStatus.PENDING_APPROVAL);
    }

    /**
     * Active accounts holding a given real role — the pool the admin picks from when
     * linking a student to a parent (role=PARENT for the options, role=STUDENT for the rows).
     */
    public List<User> listActiveByRole(Role role) {
        return userRepository.findByStatusAndRoleFetchParent(AccountStatus.ACTIVE, role);
    }

    /**
     * Approves a pending account and assigns its real role. The admin may confirm or
     * override the applicant's {@code requestedRole} — this is the anti-fraud gate.
     */
    @Transactional
    public User approve(Long id, Role role) {
        User user = find(id);
        user.approve(role);
        User saved = userRepository.save(user);
        auditLog.record(AuditEvent.of(AuditAction.ACCOUNT_APPROVED, id, "Cont aprobat").about(saved)
                .details("rol: " + roleName(role)));
        return saved;
    }

    /** Rejects a pending account (email/password or Google) without deleting it. */
    @Transactional
    public User reject(Long id) {
        User user = find(id);
        user.reject();
        User saved = userRepository.save(user);
        auditLog.record(AuditEvent.of(AuditAction.ACCOUNT_REJECTED, id, "Cont respins").about(saved));
        return saved;
    }

    /** Links a student account to a parent account (both must hold their real role). */
    @Transactional
    public User linkParent(Long studentId, Long parentId) {
        User student = find(studentId);
        User parent = find(parentId);
        boolean replaced = student.getParent() != null && !student.getParent().getId().equals(parentId);
        student.linkParent(parent);
        User saved = userRepository.save(student);
        auditLog.record(AuditEvent.of(AuditAction.PARENT_LINKED, studentId, "Părinte legat").about(saved).with(parent)
                .details(replaced ? "a înlocuit părintele de dinainte" : null));
        return saved;
    }

    private static String roleName(Role role) {
        return switch (role) {
            case ADMIN -> "profesor";
            case STUDENT -> "elev";
            case PARENT -> "părinte";
        };
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
