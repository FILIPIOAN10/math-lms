package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.mathlms.invite.InviteLinkService;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.Optional;

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
    private InviteRedeemer inviteRedeemer;
    private InviteLinkService inviteLinkService;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        inviteRedeemer = mock(InviteRedeemer.class);
        inviteLinkService = mock(InviteLinkService.class);
        service = new RegistrationService(userRepository, passwordEncoder, inviteRedeemer, inviteLinkService);
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

    // --- T1: registering through an invite, confirming the email ---

    @Test
    void aStoredLinkGivesTheRoleAndIsRememberedOnTheAccount() {
        when(inviteRedeemer.redeem("CODE")).thenReturn(new InviteRedeemer.Redemption(Role.STUDENT, 42L));
        when(passwordEncoder.encode(any())).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.registerWithInvite("ana@scoala.ro", "Ana Pop", "parola123", "CODE");

        assertThat(result.getRequestedRole()).isEqualTo(Role.STUDENT);
        assertThat(result.getInviteLinkId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void aRoleInviteLeavesNoLinkOnTheAccount() {
        when(inviteRedeemer.redeem("JWT")).thenReturn(new InviteRedeemer.Redemption(Role.PARENT, null));
        when(passwordEncoder.encode(any())).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.registerWithInvite("maria@scoala.ro", "Maria Pop", "parola123", "JWT");

        assertThat(result.getRequestedRole()).isEqualTo(Role.PARENT);
        assertThat(result.getInviteLinkId()).isNull();
    }

    @Test
    void aDeadInviteCreatesNoAccount() {
        when(inviteRedeemer.redeem("BAD")).thenThrow(new JwtException("bad"));

        assertThatThrownBy(() -> service.registerWithInvite("ana@scoala.ro", "Ana Pop", "parola123", "BAD"))
                .isInstanceOf(JwtException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void confirmingTheEmailHandsTheAccountToTheInviteLinkToFinishJoining() {
        User user = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));

        User result = service.verifyEmail("ana@scoala.ro");

        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        verify(userRepository).save(user);
        verify(inviteLinkService).completeOnboarding(user);
    }

    @Test
    void confirmingAnEmailWithoutAnAccountFails() {
        when(userRepository.findByEmail("fantoma@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("fantoma@scoala.ro")).isInstanceOf(JwtException.class);

        verify(userRepository, never()).save(any(User.class));
    }
}
