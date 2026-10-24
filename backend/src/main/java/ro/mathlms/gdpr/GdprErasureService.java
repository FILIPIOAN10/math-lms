package ro.mathlms.gdpr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.RefreshTokenService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.TotpRecoveryCodeRepository;
import ro.mathlms.auth.UserNotFoundException;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.invite.InviteLinkRepository;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.ResponsePhotoRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
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
    private final ResponsePhotoRepository photoRepository;
    private final QuizAttemptRepository attemptRepository;
    private final FileService fileService;
    private final RefreshTokenService refreshTokenService;
    private final String quizPhotosDir;
    private final InviteLinkRepository inviteLinkRepository;
    private final Clock clock;
    private final SchoolClassRepository schoolClassRepository;
    private final QuizRepository quizRepository;
    private final GdprExportRepository exportRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;

    public GdprErasureService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              VerificationTokenService tokenService, EmailService emailService,
                              EnrollmentRepository enrollmentRepository, ItemResponseRepository responseRepository,
                              ResponsePhotoRepository photoRepository,
                              QuizAttemptRepository attemptRepository, FileService fileService, RefreshTokenService refreshTokenService,
                              @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir,
                              InviteLinkRepository inviteLinkRepository, Clock clock,
                              SchoolClassRepository schoolClassRepository, QuizRepository quizRepository,
                              GdprExportRepository exportRepository, TotpRecoveryCodeRepository recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.enrollmentRepository = enrollmentRepository;
        this.responseRepository = responseRepository;
        this.photoRepository = photoRepository;
        this.attemptRepository = attemptRepository;
        this.fileService = fileService;
        this.refreshTokenService = refreshTokenService;
        this.quizPhotosDir = quizPhotosDir;
        this.inviteLinkRepository = inviteLinkRepository;
        this.clock = clock;
        this.schoolClassRepository = schoolClassRepository;
        this.quizRepository = quizRepository;
        this.exportRepository = exportRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    /**
     * Step 1: verify the requester and email a single-purpose confirmation link. A local account must
     * re-enter its password; a Google-only account (no password) is verified by the emailed link alone.
     */
    public void requestErasure(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));

        checkStaffMayLeave(user);

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
        checkStaffMayLeave(user); // again: a class may have been handed to them since the request
        Long userId = user.getId();

        // Detach the uploaded photos from the retained responses now; the files go only once that is committed, so a
        // rollback never leaves rows pointing at deleted files.
        deleteFilesAfterCommit(photoRepository.findImageKeysByStudentId(userId));
        photoRepository.deleteByStudentId(userId);
        // The teacher's comments are free text about the person and may name them; points and grades stay.
        responseRepository.clearTeacherCommentsByStudentId(userId);
        attemptRepository.clearTeacherCommentsByStudentId(userId);

        // Drop the person's own state; quiz attempts/grades stay, now anonymised via the tombstone.
        enrollmentRepository.deleteByStudentId(userId);
        exportRepository.deleteByUserId(userId); // the archives are a copy of everything about them
        recoveryCodeRepository.deleteByUserId(userId); // a credential like the password (User.erase drops the secret)
        // A parent's children are unlinked (not left on a tombstone, which would also block a new parent link), and a
        // student's unused parent links stop working (nobody may become the parent of an erased account).
        userRepository.unlinkChildrenOf(userId);
        inviteLinkRepository.findOpenForStudent(userId, clock.instant()).forEach(link -> link.revoke(clock.instant()));

        // Revoke every login session before the email changes (sessions are keyed by email).
        refreshTokenService.revokeAll(email);

        // Re-load after the bulk statements cleared the context, then anonymise in place.
        User fresh = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No account with id " + userId));
        String pseudonymEmail = pseudonymEmail(userId);
        fresh.erase(pseudonymEmail, PSEUDONYM_NAME);
        userRepository.save(fresh);

        // Audit names nobody: only the id and the deterministic pseudonym.
        log.info("GDPR erasure completed for userId={} pseudonym={}", userId, pseudonymEmail);
    }

    /**
     * Staff may erase their account too, but never leave the school without anyone to run it or orphan a class: the
     * last active ADMIN cannot leave, and a teacher (or admin) who still owns classes or tests hands them to another
     * teacher first (an ADMIN does that on the Clase page).
     */
    private void checkStaffMayLeave(User user) {
        if (user.getRole() == Role.ADMIN
                && userRepository.findByRoleAndStatusAndErasedFalse(Role.ADMIN, AccountStatus.ACTIVE).size() <= 1) {
            throw new ErasureNotAllowedException("Ești singurul administrator: numește întâi alt administrator, "
                    + "altfel nimeni nu mai poate aproba conturi.");
        }
        if ((user.getRole() == Role.ADMIN || user.getRole() == Role.TEACHER)
                && (schoolClassRepository.existsByTeacherId(user.getId()) || quizRepository.existsByTeacherId(user.getId()))) {
            throw new ErasureNotAllowedException("Mai ai clase sau teste: cere unui administrator să le dea altui profesor, "
                    + "apoi poți șterge contul.");
        }
    }

    /** Deletes the files once the transaction commits; nothing happens on a rollback. A file already gone is fine. */
    private void deleteFilesAfterCommit(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        Runnable delete = () -> keys.forEach(key -> {
            try {
                fileService.deleteImage(quizPhotosDir, key);
            } catch (IOException e) {
                log.warn("Erased photo {} could not be deleted: {}", key, e.toString());
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete.run();
            }
        });
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
