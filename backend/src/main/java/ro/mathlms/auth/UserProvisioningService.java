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

    /**
     * Resolves the {@link User} behind a Google login. If an account already
     * exists for the email (e.g. one registered locally), the Google identity is
     * linked to it — one email is always a single {@code User}. Otherwise a new
     * Google-provisioned account is created with a role from the access lists.
     */
    public User provision(String googleId, String email, String fullName) {
        String normalizedEmail = email.toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .map(existing -> linkGoogleIfNeeded(existing, googleId))
                .orElseGet(() -> userRepository.save(User.registerGoogle(
                        googleId, normalizedEmail, fullName, resolveRole(normalizedEmail))));
    }

    private User linkGoogleIfNeeded(User user, String googleId) {
        if (user.getGoogleId() == null) {
            user.linkGoogle(googleId);
            return userRepository.save(user);
        }
        return user; // already linked — nothing to persist
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
