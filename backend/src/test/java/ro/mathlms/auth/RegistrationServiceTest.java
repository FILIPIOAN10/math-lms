package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new RegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void registersPendingLocalAccountWithHashedPassword() {
        when(userRepository.existsByEmail("ana@scoala.ro")).thenReturn(false);
        when(passwordEncoder.encode("parola123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.register("ana@scoala.ro", "Ana Pop", "parola123", Role.STUDENT);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(result.isEmailVerified()).isFalse();
        assertThat(result.getPassword()).isEqualTo("HASHED");
        assertThat(result.getRequestedRole()).isEqualTo(Role.STUDENT);
        assertThat(result.getRole()).isNull(); // real role assigned only at approval
    }

    @Test
    void normalizesEmailToLowercase() {
        when(userRepository.existsByEmail("ana@scoala.ro")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.register("Ana@Scoala.RO", "Ana Pop", "parola123", Role.STUDENT);

        assertThat(result.getEmail()).isEqualTo("ana@scoala.ro");
    }

    @Test
    void rejectsDuplicateEmailWithoutSaving() {
        when(userRepository.existsByEmail("ana@scoala.ro")).thenReturn(true);

        assertThatThrownBy(() ->
                service.register("ana@scoala.ro", "Ana Pop", "parola123", Role.STUDENT))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("ana@scoala.ro");

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejectsBlankPassword() {
        assertThatThrownBy(() ->
                service.register("ana@scoala.ro", "Ana Pop", "  ", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");

        verify(userRepository, never()).save(any(User.class));
    }
}
