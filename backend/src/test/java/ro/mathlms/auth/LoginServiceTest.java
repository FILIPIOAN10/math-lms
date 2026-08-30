package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private LoginService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new LoginService(userRepository, passwordEncoder);
    }

    /** A local account that is verified and approved (role assigned). */
    private User activeUser() {
        User user = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        user.verifyEmail();
        user.approve(Role.STUDENT);
        return user;
    }

    @Test
    void authenticatesActiveUserWithMatchingPassword() {
        User user = activeUser();
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("parola123", "HASH")).thenReturn(true);

        User result = service.authenticate("Ana@Scoala.RO", "parola123");

        assertThat(result).isSameAs(user);
    }

    @Test
    void rejectsUnknownEmail() {
        when(userRepository.findByEmail("nimeni@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("nimeni@scoala.ro", "parola123"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsWrongPassword() {
        User user = activeUser();
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("gresit", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("ana@scoala.ro", "gresit"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsGoogleOnlyAccountWithoutPassword() {
        User googleUser = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT); // password null
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(googleUser));

        assertThatThrownBy(() -> service.authenticate("ana@scoala.ro", "parola123"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsUnverifiedEmail() {
        User user = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("parola123", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate("ana@scoala.ro", "parola123"))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void rejectsVerifiedButNotYetApprovedAccount() {
        User user = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        user.verifyEmail(); // PENDING_APPROVAL, still not ACTIVE
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("parola123", "HASH")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate("ana@scoala.ro", "parola123"))
                .isInstanceOf(AccountNotActiveException.class);
    }
}
