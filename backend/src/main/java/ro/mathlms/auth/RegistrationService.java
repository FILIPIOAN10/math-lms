package ro.mathlms.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * Registers local (email/password) accounts. The new account starts at
 * PENDING_VERIFICATION and carries the role requested via the invite link;
 * its real role is assigned later, when an admin approves it.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String fullName, String rawPassword, Role requestedRole) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        String normalizedEmail = email.toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = User.registerLocal(normalizedEmail, fullName, passwordHash, requestedRole);
        return userRepository.save(user);
    }
}
