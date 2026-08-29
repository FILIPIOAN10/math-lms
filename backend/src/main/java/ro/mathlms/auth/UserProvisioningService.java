package ro.mathlms.auth;

import org.springframework.stereotype.Service;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public UserProvisioningService(UserRepository userRepository, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    public User provision(String email, String fullName) {
        String normalizedEmail = email.toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userRepository.save(
                        new User(normalizedEmail, fullName, resolveRole(normalizedEmail))));
    }

    private Role resolveRole(String email) {
        if (contains(authProperties.adminEmails(), email)) {
            return Role.ADMIN;
        }
        if (contains(authProperties.allowedEmails(), email)) {
            return Role.STUDENT;
        }
        throw new EmailNotAllowedException(email);
    }

    private boolean contains(List<String> emails, String email) {
        return emails != null && emails.stream().anyMatch(e -> e.trim().equalsIgnoreCase(email));
    }
}
