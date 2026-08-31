package ro.mathlms.auth;

import org.springframework.stereotype.Service;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

/** Admin onboarding actions: list pending accounts, approve/reject, link a parent. */
@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Accounts that passed email verification and await an admin decision. */
    public List<User> listPending() {
        return userRepository.findByStatus(AccountStatus.PENDING_APPROVAL);
    }

    /**
     * Approves a pending account and assigns its real role. The admin may confirm or
     * override the applicant's {@code requestedRole} — this is the anti-fraud gate.
     */
    public User approve(Long id, Role role) {
        User user = find(id);
        user.approve(role);
        return userRepository.save(user);
    }

    /** Rejects a pending account (email/password or Google) without deleting it. */
    public User reject(Long id) {
        User user = find(id);
        user.reject();
        return userRepository.save(user);
    }

    /** Links a student account to a parent account (both must hold their real role). */
    public User linkParent(Long studentId, Long parentId) {
        User student = find(studentId);
        User parent = find(parentId);
        student.linkParent(parent);
        return userRepository.save(student);
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
