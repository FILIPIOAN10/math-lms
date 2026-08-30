package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private VerificationTokenService verificationTokenService;
    private EmailService emailService;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        verificationTokenService = mock(VerificationTokenService.class);
        emailService = mock(EmailService.class);
        service = new PasswordResetService(
                userRepository, passwordEncoder, verificationTokenService, emailService);
    }

    @Test
    void requestResetEmailsLinkWhenAccountExists() {
        User user = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));
        when(verificationTokenService.generate("ana@scoala.ro", TokenPurpose.PASSWORD_RESET))
                .thenReturn("RESET");

        service.requestReset("Ana@Scoala.RO");

        verify(emailService).sendPasswordResetEmail("ana@scoala.ro", "RESET");
    }

    @Test
    void requestResetStaysSilentForUnknownEmail() {
        when(userRepository.findByEmail("nimeni@scoala.ro")).thenReturn(Optional.empty());

        service.requestReset("nimeni@scoala.ro");

        verifyNoInteractions(verificationTokenService, emailService);
    }

    @Test
    void resetPasswordSetsNewHashedPassword() {
        User user = User.registerLocal("ana@scoala.ro", "Ana Pop", "OLD_HASH", Role.STUDENT);
        when(verificationTokenService.verify("RESET", TokenPurpose.PASSWORD_RESET))
                .thenReturn("ana@scoala.ro");
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("parolaNoua1")).thenReturn("NEW_HASH");

        service.resetPassword("RESET", "parolaNoua1");

        assertThat(user.getPassword()).isEqualTo("NEW_HASH");
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordFailsWhenTokenHasNoAccount() {
        when(verificationTokenService.verify("RESET", TokenPurpose.PASSWORD_RESET))
                .thenReturn("fantoma@scoala.ro");
        when(userRepository.findByEmail("fantoma@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("RESET", "parolaNoua1"))
                .isInstanceOf(JwtException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsBlankPassword() {
        assertThatThrownBy(() -> service.resetPassword("RESET", "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(verificationTokenService, never()).verify(anyString(), eq(TokenPurpose.PASSWORD_RESET));
    }
}
