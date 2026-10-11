package ro.mathlms.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.invite.InviteLinkService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final AuthProperties authProperties;
    private final InviteRedeemer inviteRedeemer;
    private final InviteLinkService inviteLinkService;

    public UserProvisioningService(UserRepository userRepository,
                                   AuthProperties authProperties,
                                   InviteRedeemer inviteRedeemer,
                                   InviteLinkService inviteLinkService) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
        this.inviteRedeemer = inviteRedeemer;
        this.inviteLinkService = inviteLinkService;
    }

    /**
     * Resolves the {@link User} behind a Google login, optionally carrying an invite
     * token from the authorization request. If an account already exists for the email
     * (e.g. one registered locally), the Google identity is linked to it — one email is
     * always a single {@code User}. A brand-new login is provisioned as:
     * <ul>
     *   <li>an ACTIVE account if the email is on the admin/allowed lists (pre-trusted);</li>
     *   <li>otherwise, if an invite token is present, a PENDING_APPROVAL account whose
     *       requested role comes from the invite (admin confirms it on approval) — except
     *       that a stored class/parent link (T1) activates, enrols / links it right away;</li>
     *   <li>otherwise the login is rejected.</li>
     * </ul>
     */
    @Transactional
    public User provision(String googleId, String email, String fullName, String inviteToken) {
        String normalizedEmail = email.toLowerCase();

        return userRepository.findByEmail(normalizedEmail)
                .map(existing -> linkGoogleIfNeeded(existing, googleId))
                .orElseGet(() -> {
                    User created = userRepository.save(newAccount(googleId, normalizedEmail, fullName, inviteToken));
                    inviteLinkService.completeOnboarding(created); // Google already verified the email
                    return created;
                });
    }

    private User newAccount(String googleId, String email, String fullName, String inviteToken) {
        if (isPreTrusted(email)) {
            return User.registerGoogle(googleId, email, fullName, resolveRole(email));
        }
        if (inviteToken != null && !inviteToken.isBlank()) {
            InviteRedeemer.Redemption redemption = inviteRedeemer.redeem(inviteToken);
            User user = User.registerGoogleInvited(googleId, email, fullName, redemption.role());
            if (redemption.inviteLinkId() != null) {
                user.joinedThrough(redemption.inviteLinkId());
            }
            return user;
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
