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
    private final InviteTokenService inviteTokenService;

    public UserProvisioningService(UserRepository userRepository,
                                   AuthProperties authProperties,
                                   InviteTokenService inviteTokenService) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
        this.inviteTokenService = inviteTokenService;
    }

    /**
     * Resolves the {@link User} behind a Google login, optionally carrying an invite
     * token from the authorization request. If an account already exists for the email
     * (e.g. one registered locally), the Google identity is linked to it — one email is
     * always a single {@code User}. A brand-new login is provisioned as:
     * <ul>
     *   <li>an ACTIVE account if the email is on the admin/allowed lists (pre-trusted);</li>
     *   <li>otherwise, if an invite token is present, a PENDING_APPROVAL account whose
     *       requested role comes from the invite (admin confirms it on approval);</li>
     *   <li>otherwise the login is rejected.</li>
     * </ul>
     */
    public User provision(String googleId, String email, String fullName, String inviteToken) {
        String normalizedEmail = email.toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .map(existing -> linkGoogleIfNeeded(existing, googleId))
                .orElseGet(() -> userRepository.save(
                        newAccount(googleId, normalizedEmail, fullName, inviteToken)));
    }

    private User newAccount(String googleId, String email, String fullName, String inviteToken) {
        if (isPreTrusted(email)) {
            return User.registerGoogle(googleId, email, fullName, resolveRole(email));
        }
        if (inviteToken != null && !inviteToken.isBlank()) {
            Role requestedRole = inviteTokenService.verify(inviteToken);
            return User.registerGoogleInvited(googleId, email, fullName, requestedRole);
        }
        throw new EmailNotAllowedException(email);
    }

    private boolean isPreTrusted(String email) {
        return contains(authProperties.adminEmails(), email)
                || contains(authProperties.allowedEmails(), email);
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
