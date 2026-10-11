package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.invite.InviteLinkService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * Registers local (email/password) accounts. The new account starts at
 * PENDING_VERIFICATION and carries the role requested via the invite link.
 * Confirming the email then either activates it straight away (it came through
 * a stored class/parent link, T1) or leaves it for an admin to approve.
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InviteRedeemer inviteRedeemer;
    private final InviteLinkService inviteLinkService;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               InviteRedeemer inviteRedeemer, InviteLinkService inviteLinkService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.inviteRedeemer = inviteRedeemer;
        this.inviteLinkService = inviteLinkService;
    }

    /**
     * Registers through an invite link. The role comes from the link, never from the client. One transaction: if the
     * email is already taken, the use a stored link gave up is handed back.
     */
    @Transactional
    public User registerWithInvite(String email, String fullName, String rawPassword, String inviteToken) {
        InviteRedeemer.Redemption redemption = inviteRedeemer.redeem(inviteToken);
        User user = newLocalAccount(email, fullName, rawPassword, redemption.role());
        if (redemption.inviteLinkId() != null) {
            user.joinedThrough(redemption.inviteLinkId());
        }
        return userRepository.save(user);
    }

    public User register(String email, String fullName, String rawPassword, Role requestedRole) {
        return userRepository.save(newLocalAccount(email, fullName, rawPassword, requestedRole));
    }

    /**
     * Confirms the email carried by a verification token and, for an account that came through a stored link,
     * finishes joining (active, enrolled / linked). Returns the account in its new state.
     */
    @Transactional
    public User verifyEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new JwtException("No account for this token"));
        user.verifyEmail();
        userRepository.save(user);
        inviteLinkService.completeOnboarding(user);
        return user;
    }

    private User newLocalAccount(String email, String fullName, String rawPassword, Role requestedRole) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        String normalizedEmail = email.toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        return User.registerLocal(normalizedEmail, fullName, passwordHash, requestedRole);
    }
}
