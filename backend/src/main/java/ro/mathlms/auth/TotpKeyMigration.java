package ro.mathlms.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

/**
 * At every start, re-seals the authenticator secrets that are not yet in the current format with the current
 * TOTP_ENC_KEY: those stored before phase 2 (no version, key derived from JWT_SECRET) and those still under
 * TOTP_ENC_KEY_PREVIOUS during a rotation. Staff keep their phones as they are; once the log says 0 left, the previous
 * key can be removed from the environment. Idempotent (already-current values are skipped) and cheap (a few staff rows).
 *
 * <p>A secret no configured key opens is logged and left alone rather than stopping the app: that person signs in with
 * a recovery code (or an admin turns their two-step sign-in off) - the site stays up for everyone else.
 */
@Component
public class TotpKeyMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TotpKeyMigration.class);

    private final UserRepository userRepository;
    private final TotpSecretCipher cipher;

    public TotpKeyMigration(UserRepository userRepository, TotpSecretCipher cipher) {
        this.userRepository = userRepository;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrate();
    }

    /** Re-encrypts what needs it; returns how many secrets were re-sealed. */
    @Transactional
    public int migrate() {
        int rewrapped = 0;
        int unreadable = 0;
        for (User user : userRepository.findByTotpSecretIsNotNull()) {
            String stored = user.getTotpSecret();
            if (cipher.isCurrent(stored)) {
                continue;
            }
            try {
                user.rewrapTwoFactorSecret(cipher.reencrypt(stored));
                rewrapped++;
            } catch (IllegalStateException e) {
                unreadable++;
                log.error("TOTP secret of user {} opens with no configured key: they need a recovery code", user.getId());
            }
        }
        if (rewrapped > 0 || unreadable > 0) {
            log.info("TOTP key migration: {} secret(s) re-encrypted with TOTP_ENC_KEY, {} unreadable", rewrapped, unreadable);
        }
        return rewrapped;
    }
}
