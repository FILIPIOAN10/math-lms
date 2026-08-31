package ro.mathlms.auth;

import org.springframework.stereotype.Service;
import ro.mathlms.user.AccountStatus;
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
}
