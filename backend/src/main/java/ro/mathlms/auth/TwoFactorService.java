package ro.mathlms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * F8: two-step sign-in with an authenticator app, for staff — required for an ADMIN (they hand out every other role),
 * optional for a TEACHER. Enrolling is two calls: {@link #setup} makes a secret (kept in Redis for 10 minutes, never in
 * the database until proven), {@link #enable} turns it on once the app's first code matches and hands out ten
 * single-use recovery codes (shown once; only their hashes are kept). A code is accepted once: its step is recorded.
 */
@Service
public class TwoFactorService {

    public static final String ISSUER = "Math LMS";
    static final Duration SETUP_TTL = Duration.ofMinutes(10);
    static final int RECOVERY_CODES = 10;
    private static final String SETUP_PREFIX = "2fa-setup:";
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o, 1/l/i
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What the account page shows. */
    public record Status(boolean enabled, boolean required, long recoveryCodesLeft) {
    }

    /** What the app is given to enrol: the secret (to type) and the otpauth address (the QR code). */
    public record Setup(String secret, String otpauthUri) {
    }

    private final UserRepository userRepository;
    private final TotpRecoveryCodeRepository recoveryCodes;
    private final TotpSecretCipher cipher;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final boolean requiredForAdmin;

    public TwoFactorService(UserRepository userRepository, TotpRecoveryCodeRepository recoveryCodes,
                            TotpSecretCipher cipher, StringRedisTemplate redis, Clock clock,
                            @Value("${app.auth.two-factor.required-for-admin:true}") boolean requiredForAdmin) {
        this.userRepository = userRepository;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.redis = redis;
        this.clock = clock;
        this.requiredForAdmin = requiredForAdmin;
    }

    /** Whether this account must have two-step sign-in (an ADMIN, unless switched off for tests/dev). */
    public boolean requiredFor(User user) {
        return requiredForAdmin && user.getRole() == Role.ADMIN;
    }

    /** An ADMIN without it yet: the staff pages stay closed until they enrol. */
    public boolean mustEnrol(User user) {
        return requiredFor(user) && !user.isTwoFactorEnabled();
    }

    @Transactional(readOnly = true)
    public Status status(String email) {
        User user = staff(email);
        return new Status(user.isTwoFactorEnabled(), requiredFor(user), recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId()));
    }

    public Setup setup(String email) {
        User user = staff(email);
        if (user.isTwoFactorEnabled()) {
            throw new TwoFactorException("Autentificarea în doi pași e deja activă.");
        }
        String secret = Totp.newSecret();
        redis.opsForValue().set(SETUP_PREFIX + user.getId(), cipher.encrypt(secret), SETUP_TTL);
        return new Setup(secret, Totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    /** Turns it on if {@code code} is the app's current code for the secret from {@link #setup}; the recovery codes. */
    @Transactional
    public List<String> enable(String email, String code) {
        User user = staff(email);
        String pending = redis.opsForValue().get(SETUP_PREFIX + user.getId());
        if (pending == null) {
            throw new TwoFactorException("Codul QR a expirat. Începe din nou.");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(pending), code, clock.instant(), null);
        if (step.isEmpty()) {
            throw new TwoFactorException("Codul nu se potrivește. Verifică ora telefonului și încearcă din nou.");
        }
        user.enableTwoFactor(pending, step.getAsLong(), clock.instant());
        userRepository.save(user);
        redis.delete(SETUP_PREFIX + user.getId());
        return newRecoveryCodes(user);
    }

    /** New recovery codes (the old ones stop working), after a current code from the app. */
    @Transactional
    public List<String> regenerateRecoveryCodes(String email, String code) {
        User user = staff(email);
        requireCurrentCode(user, code);
        return newRecoveryCodes(user);
    }

    /** A TEACHER may turn it off (with a current code); for an ADMIN it is required. */
    @Transactional
    public void disable(String email, String code) {
        User user = staff(email);
        if (requiredFor(user)) {
            throw new TwoFactorException("Pentru administratori autentificarea în doi pași e obligatorie.");
        }
        requireCurrentCode(user, code);
        user.disableTwoFactor();
        userRepository.save(user);
        recoveryCodes.deleteByUserId(user.getId());
    }

    /** The second step of a sign-in: an app code (once per step) or an unused recovery code (once). */
    @Transactional
    public boolean verifySignIn(User user, String codeOrRecovery) {
        if (!user.isTwoFactorEnabled() || codeOrRecovery == null) {
            return false;
        }
        String given = codeOrRecovery.strip();
        if (given.matches("\\d{6}")) {
            OptionalLong step = Totp.verify(cipher.decrypt(user.getTotpSecret()), given, clock.instant(), user.getTotpLastStep());
            step.ifPresent(s -> {
                user.useTotpStep(s);
                userRepository.save(user);
            });
            return step.isPresent();
        }
        return recoveryCodes.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), hash(given))
                .map(recovery -> {
                    recovery.use(clock.instant());
                    return true;
                })
                .orElse(false);
    }

    private void requireCurrentCode(User user, String code) {
        if (!user.isTwoFactorEnabled()) {
            throw new TwoFactorException("Autentificarea în doi pași nu e activă.");
        }
        if (!verifySignIn(user, code)) {
            throw new TwoFactorException("Codul nu se potrivește.");
        }
    }

    private List<String> newRecoveryCodes(User user) {
        recoveryCodes.deleteByUserId(user.getId());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = randomGroup() + "-" + randomGroup() + "-" + randomGroup();
            codes.add(code);
            recoveryCodes.save(new TotpRecoveryCode(user.getId(), hash(code)));
        }
        return codes;
    }

    private static String randomGroup() {
        StringBuilder group = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            group.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return group.toString();
    }

    /** Recovery codes are long random strings, so a plain SHA-256 is enough (no slow hash needed). */
    static String hash(String code) {
        try {
            String normalised = code.strip().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private User staff(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("No account"));
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.TEACHER) {
            throw new TwoFactorException("Autentificarea în doi pași e pentru profesori și administratori.");
        }
        return user;
    }
}
