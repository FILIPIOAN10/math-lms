package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProvisioningServiceTest {

    private UserRepository userRepository;
    private UserProvisioningService service;
    private InviteTokenService inviteTokenService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        AuthProperties properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of("elev@gmail.com"),
                "test-secret-at-least-32-characters-long!!",
                60);
        inviteTokenService = new InviteTokenService(properties);
        service = new UserProvisioningService(userRepository, properties, inviteTokenService);
    }

    @Test
    void createsActiveAdminForConfiguredAdminEmail() {
        when(userRepository.findByEmail("profesor@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("google-sub-1", "profesor@gmail.com", "Prof Ion", null);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getEmail()).isEqualTo("profesor@gmail.com");
        assertThat(result.getGoogleId()).isEqualTo("google-sub-1");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createsStudentForAllowedEmail() {
        when(userRepository.findByEmail("elev@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("google-sub-2", "elev@gmail.com", "Ana Pop", null);

        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void normalizesEmailToLowercase() {
        when(userRepository.findByEmail("profesor@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("google-sub-3", "Profesor@Gmail.COM", "Prof Ion", null);

        assertThat(result.getEmail()).isEqualTo("profesor@gmail.com");
    }

    @Test
    void rejectsEmailNotOnAnyList() {
        when(userRepository.findByEmail("strain@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provision("google-sub-4", "strain@gmail.com", "Cineva", null))
                .isInstanceOf(EmailNotAllowedException.class)
                .hasMessageContaining("strain@gmail.com");

        verify(userRepository, never()).save(any(User.class));
    }

    // --- Step 1.6i: account linking ---

    @Test
    void linksGoogleIdToExistingLocalAccount() {
        // A locally-registered account (not on any list) logs in with Google.
        User local = User.registerLocal("elev@scoala.ro", "Elev Local", "HASH", Role.STUDENT);
        when(userRepository.findByEmail("elev@scoala.ro")).thenReturn(Optional.of(local));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("google-sub-5", "elev@scoala.ro", "Elev Local", null);

        assertThat(result).isSameAs(local);
        assertThat(result.getGoogleId()).isEqualTo("google-sub-5");
        verify(userRepository).save(local);
    }

    @Test
    void doesNotReSaveAlreadyLinkedAccount() {
        User linked = User.registerGoogle("google-sub-6", "profesor@gmail.com", "Prof Ion", Role.ADMIN);
        when(userRepository.findByEmail("profesor@gmail.com")).thenReturn(Optional.of(linked));

        User result = service.provision("google-sub-6", "profesor@gmail.com", "Prof Ion", null);

        assertThat(result).isSameAs(linked);
        verify(userRepository, never()).save(any(User.class));
    }

    // --- Step 1.6l-7: Google login via an invite link ---

    @Test
    void createsPendingApprovalFromInviteForEmailNotOnAnyList() {
        when(userRepository.findByEmail("strain@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        String invite = inviteTokenService.generate(Role.PARENT);

        User result = service.provision("google-sub-7", "strain@gmail.com", "Parinte Nou", invite);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(result.getRequestedRole()).isEqualTo(Role.PARENT);
        assertThat(result.getRole()).isNull(); // real role assigned on admin approval
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getGoogleId()).isEqualTo("google-sub-7");
    }

    @Test
    void preTrustedEmailStaysActiveEvenWithAnInvite() {
        when(userRepository.findByEmail("elev@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        String invite = inviteTokenService.generate(Role.PARENT);

        // The allow-list wins over the invited role: pre-listed emails are pre-approved.
        User result = service.provision("google-sub-8", "elev@gmail.com", "Ana Pop", invite);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void rejectsUnknownEmailWithAnInvalidInvite() {
        when(userRepository.findByEmail("strain@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.provision("google-sub-9", "strain@gmail.com", "Cineva", "not-a-valid-token"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);

        verify(userRepository, never()).save(any(User.class));
    }
}
