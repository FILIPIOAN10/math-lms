package ro.mathlms.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/** The signed-in user's own account: what "Contul meu" shows, and the password change. */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public AccountDto account(String email) {
        User user = requireUser(email);
        return new AccountDto(user.getEmail(), user.getFullName(), user.getRole(),
                user.getPassword() != null, user.getGoogleId() != null);
    }

    /**
     * Changes the password after checking the current one. Every OTHER device is signed out (a password is usually
     * changed because it may have leaked); the device making the change stays signed in. Their short-lived access
     * cookies still work until they expire (at most an hour) - only the refresh sessions are revoked.
     * A Google-only account has no current password to prove, so it sets one through the emailed reset link instead.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword, String currentRefreshToken) {
        User user = requireUser(email);
        if (user.getPassword() == null) {
            throw new PasswordChangeException(
                    "Contul tău se conectează cu Google și nu are parolă. Ca să-ți setezi una, folosește „Ai uitat parola” de pe pagina de login.");
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new PasswordChangeException("Parola actuală nu e corectă.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new PasswordChangeException("Parola nouă trebuie să fie diferită de cea actuală.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.listSessions(email, currentRefreshToken).stream()
                .filter(session -> !session.current())
                .forEach(session -> refreshTokenService.revokeSession(email, session.sessionId()));
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));
    }
}
