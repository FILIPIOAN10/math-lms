package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * Handles the "forgot password" flow. Requesting a reset never reveals whether
 * an account exists (the controller always answers 200); the reset itself is
 * gated by a short-lived signed token.
 *
 * <p>Note: tokens are stateless (HMAC), so a single token cannot be revoked
 * before it expires — mitigated by the 1h TTL. See VerificationTokenService.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokenService;
    private final EmailService emailService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                VerificationTokenService verificationTokenService,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationTokenService = verificationTokenService;
        this.emailService = emailService;
    }

    /** Emails a reset link if the account exists; silently no-ops otherwise. */
    public void requestReset(String email) {
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            String token = verificationTokenService.generate(user.getEmail(), TokenPurpose.PASSWORD_RESET);
            emailService.sendPasswordResetEmail(user.getEmail(), token);
        });
    }

    /** Verifies the token and sets a new BCrypt-hashed password. */
    public void resetPassword(String token, String newRawPassword) {
        if (newRawPassword == null || newRawPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        String email = verificationTokenService.verify(token, TokenPurpose.PASSWORD_RESET);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new JwtException("No account for this token"));

        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }
}
