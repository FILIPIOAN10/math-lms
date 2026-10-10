package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private static final String EMAIL = "ana@scoala.ro";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // fast, still real hashing
    private final AccountService service = new AccountService(userRepository, encoder, refreshTokenService);

    private User localUser(String rawPassword) {
        User user = new User(EMAIL, "Ana Student", Role.STUDENT);
        user.setPassword(encoder.encode(rawPassword));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private DeviceSessionResponse session(String id, boolean current) {
        return new DeviceSessionResponse(id, "Chrome pe Windows", "127.0.0.1", 0L, 0L, current);
    }

    // --- what "Contul meu" shows ---

    @Test
    void describesAPasswordAccount() {
        localUser("parola-veche");

        AccountDto account = service.account(EMAIL);

        assertThat(account.email()).isEqualTo(EMAIL);
        assertThat(account.fullName()).isEqualTo("Ana Student");
        assertThat(account.role()).isEqualTo(Role.STUDENT);
        assertThat(account.hasPassword()).isTrue();
        assertThat(account.googleLinked()).isFalse();
    }

    @Test
    void describesAGoogleOnlyAccount() {
        User google = User.registerGoogle("google-123", EMAIL, "Ana Student", Role.STUDENT);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(google));

        AccountDto account = service.account(EMAIL);

        assertThat(account.hasPassword()).isFalse();
        assertThat(account.googleLinked()).isTrue();
    }

    // --- changing the password ---

    @Test
    void changesThePasswordAndSignsOutEveryOtherDevice() {
        User user = localUser("parola-veche");
        when(refreshTokenService.listSessions(EMAIL, "tok-curent"))
                .thenReturn(List.of(session("s1", true), session("s2", false), session("s3", false)));

        service.changePassword(EMAIL, "parola-veche", "parola-noua-123", "tok-curent");

        assertThat(encoder.matches("parola-noua-123", user.getPassword())).isTrue();
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeSession(EMAIL, "s2");
        verify(refreshTokenService).revokeSession(EMAIL, "s3");
        verify(refreshTokenService, never()).revokeSession(EMAIL, "s1"); // this device stays signed in
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        User user = localUser("parola-veche");
        String before = user.getPassword();

        assertThatThrownBy(() -> service.changePassword(EMAIL, "gresita", "parola-noua-123", "tok"))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessage("Parola actuală nu e corectă.");
        assertThat(user.getPassword()).isEqualTo(before);
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeSession(anyString(), anyString());
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() {
        localUser("parola-veche");

        assertThatThrownBy(() -> service.changePassword(EMAIL, "parola-veche", "parola-veche", "tok"))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessage("Parola nouă trebuie să fie diferită de cea actuală.");
    }

    @Test
    void aGoogleOnlyAccountIsSentToTheResetFlowInstead() {
        User google = User.registerGoogle("google-123", EMAIL, "Ana Student", Role.STUDENT);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> service.changePassword(EMAIL, "orice", "parola-noua-123", "tok"))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessageContaining("Ai uitat parola");
        verify(userRepository, never()).save(any());
    }
}
