package ro.mathlms.gdpr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.UserNotFoundException;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * GDPR Art. 17 — erasure, two-step. {@link #requestErasure} proves the requester (password for local
 * accounts) and emails a confirmation link; {@link #confirmErasure} proves control of the mailbox and
 * then anonymises the account. Nothing is deleted until the token is spent.
 *
 * <p>The split for this LMS: enrollments and uploaded rezolvare photos are the person's own data and
 * are <strong>deleted</strong>; quiz attempts and grades are <strong>kept, anonymised</strong> —
 * they keep pointing at the now-pseudonymised user row (a tombstone that can never authenticate).
 */
@Service
public class GdprErasureService {

    private static final Logger log = LoggerFactory.getLogger(GdprErasureService.class);
    private static final String PSEUDONYM_NAME = "Utilizator șters";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final EmailService emailService;
    private final EnrollmentRepository enrollmentRepository;
    private final ItemResponseRepository responseRepository;
    private final FileService fileService;
    private final String quizPhotosDir;

    public GdprErasureService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              VerificationTokenService tokenService, EmailService emailService,
                              EnrollmentRepository enrollmentRepository, ItemResponseRepository responseRepository,
                              FileService fileService,
                              @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.enrollmentRepository = enrollmentRepository;
        this.responseRepository = responseRepository;
        this.fileService = fileService;
        this.quizPhotosDir = quizPhotosDir;
    }

    /**
     * Step 1: verify the requester and email a single-purpose confirmation link. A local account must
     * re-enter its password; a Google-only account (no password) is verified by the emailed link alone.
     */
    public void requestErasure(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));

        if (user.getPassword() != null) {
            if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
                throw new BadCredentialsException("Invalid credentials");
            }
        }

        String token = tokenService.generate(user.getEmail(), TokenPurpose.ERASE_ACCOUNT);
        emailService.sendErasureConfirmationEmail(user.getEmail(), token);
    }

    /**
     * Step 2: spend the token and erase. Idempotent — a replayed token (whose email no longer resolves,
     * or whose account is already erased) is a quiet no-op.
     */
    @Transactional
    public void confirmErasure(String token) {
        String email = tokenService.verify(token, TokenPurpose.ERASE_ACCOUNT);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isErased()) {
            return;
        }
        Long userId = user.getId();

        // Delete the uploaded photos (files first, then detach them from the retained responses).
        for (String key : responseRepository.findImageKeysByStudentId(userId)) {
            try {
                fileService.deleteImage(quizPhotosDir, key);
            } catch (IOException ignored) {
                // A file already gone must not block the erasure.
            }
        }
        responseRepository.clearPhotosByStudentId(userId);

        // Drop the person's own state; quiz attempts/grades stay, now anonymised via the tombstone.
        enrollmentRepository.deleteByStudentId(userId);

        // Re-load after the bulk statements cleared the context, then anonymise in place.
        User fresh = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No account with id " + userId));
        String pseudonymEmail = pseudonymEmail(userId);
        fresh.erase(pseudonymEmail, PSEUDONYM_NAME);
        userRepository.save(fresh);

        // Audit names nobody: only the id and the deterministic pseudonym.
        log.info("GDPR erasure completed for userId={} pseudonym={}", userId, pseudonymEmail);
    }

    /** Deterministic, non-reversible handle so retained rows still cluster without revealing who. */
    private static String pseudonymEmail(Long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("gdpr-erasure:" + userId).getBytes(StandardCharsets.UTF_8));
            return "erased-" + HexFormat.of().formatHex(hash).substring(0, 16) + "@erased.invalid";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
