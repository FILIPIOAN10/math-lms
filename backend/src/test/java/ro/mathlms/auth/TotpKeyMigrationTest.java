package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static ro.mathlms.auth.TotpSecretCipherTest.NEW;
import static ro.mathlms.auth.TotpSecretCipherTest.OLD;
import static ro.mathlms.auth.TotpSecretCipherTest.cipher;
import static ro.mathlms.auth.TotpSecretCipherTest.sealLikeBeforePhase2;

/** Phase 2: staff keep their authenticator apps - every stored secret is re-sealed with TOTP_ENC_KEY at start. */
class TotpKeyMigrationTest {

    private static final String UNKNOWN = "some-other-totp-key-nobody-configured-32ch";

    private static User staffWith(String storedSecret) {
        User user = User.registerGoogle("g-" + storedSecret.hashCode(), "staff@scoala.ro", "Staff", Role.ADMIN);
        user.enableTwoFactor(storedSecret, 1L, Instant.now());
        return user;
    }

    @Test
    void reSealsOldSecretsSkipsCurrentOnesAndLeavesUnreadableOnesAlone() throws Exception {
        TotpSecretCipher rotating = cipher(NEW, OLD);
        User beforePhase2 = staffWith(sealLikeBeforePhase2("AAAAAAAAAAAAAAAA"));
        User underOld = staffWith(cipher(OLD, null).encrypt("BBBBBBBBBBBBBBBB"));
        User current = staffWith(rotating.encrypt("CCCCCCCCCCCCCCCC"));
        String currentBefore = current.getTotpSecret();
        User unreadable = staffWith(cipher(UNKNOWN, null).encrypt("DDDDDDDDDDDDDDDD"));
        String unreadableBefore = unreadable.getTotpSecret();
        UserRepository users = mock(UserRepository.class);
        when(users.findByTotpSecretIsNotNull()).thenReturn(List.of(beforePhase2, underOld, current, unreadable));

        int rewrapped = new TotpKeyMigration(users, rotating).migrate();

        TotpSecretCipher afterRotation = cipher(NEW, null);
        assertThat(rewrapped).isEqualTo(2);
        assertThat(afterRotation.decrypt(beforePhase2.getTotpSecret())).isEqualTo("AAAAAAAAAAAAAAAA");
        assertThat(afterRotation.decrypt(underOld.getTotpSecret())).isEqualTo("BBBBBBBBBBBBBBBB");
        assertThat(current.getTotpSecret()).isEqualTo(currentBefore);
        assertThat(unreadable.getTotpSecret()).isEqualTo(unreadableBefore);
        // A second start finds nothing left to do.
        assertThat(new TotpKeyMigration(users, rotating).migrate()).isZero();
    }
}
