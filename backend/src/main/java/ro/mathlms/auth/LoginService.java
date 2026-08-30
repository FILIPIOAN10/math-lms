package ro.mathlms.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * Authenticates local (email/password) accounts. Only fully approved
 * ({@link AccountStatus#ACTIVE}) accounts are allowed a session; verified but
 * still-pending accounts get a distinct, safe-to-reveal reason.
 */
@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Google-only accounts have no local password.
        if (user.getPassword() == null
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isEmailVerified()) {
            throw AccountNotActiveException.emailNotVerified();
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw AccountNotActiveException.forStatus(user.getStatus());
        }

        return user;
    }
}
