package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.mockito.ArgumentMatchers.anyString;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegistrationService registrationService = mock(RegistrationService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final VerificationTokenService verificationTokenService = mock(VerificationTokenService.class);
    private final InviteTokenService inviteTokenService = mock(InviteTokenService.class);
    private final LoginService loginService = mock(LoginService.class);
    private final JwtCookieFactory jwtCookieFactory = mock(JwtCookieFactory.class);
    private final PasswordResetService passwordResetService = mock(PasswordResetService.class);
    private final AuthController controller = new AuthController(
            userRepository, registrationService, emailService, verificationTokenService,
            inviteTokenService, loginService, jwtCookieFactory, passwordResetService);

    @Test
    void meReturnsUserWhenAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("profesor@gmail.com");
        when(userRepository.findByEmail("profesor@gmail.com"))
                .thenReturn(Optional.of(new User("profesor@gmail.com", "Prof Ion", Role.ADMIN)));

        ResponseEntity<UserDto> response = controller.me(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("profesor@gmail.com");
        assertThat(response.getBody().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void meExposesStatusForPendingRolelessAccount() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ana@scoala.ro");
        User pending = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(pending));

        ResponseEntity<UserDto> response = controller.me(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(response.getBody().role()).isNull();
    }

    @Test
    void meReturns401WhenNoAuthentication() {
        ResponseEntity<UserDto> response = controller.me(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meReturns401WhenUserNotInDatabase() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fantoma@gmail.com");
        when(userRepository.findByEmail("fantoma@gmail.com")).thenReturn(Optional.empty());

        ResponseEntity<UserDto> response = controller.me(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutClearsCookie() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        controller.logout(servletResponse);

        Cookie cookie = servletResponse.getCookie(JwtCookieSuccessHandler.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    // --- Step 1.6f: register + verify-email ---

    @Test
    void registerDerivesRoleFromInviteThenSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest(
                "ana@scoala.ro", "Ana Pop", "parola123", "INVITE");
        User created = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        when(inviteTokenService.verify("INVITE")).thenReturn(Role.STUDENT);
        when(registrationService.register("ana@scoala.ro", "Ana Pop", "parola123", Role.STUDENT))
                .thenReturn(created);
        when(verificationTokenService.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL))
                .thenReturn("TOKEN");

        ResponseEntity<Void> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(emailService).sendVerificationEmail("ana@scoala.ro", "TOKEN");
    }

    @Test
    void registerRejectsInvalidInviteWithoutCreatingAccount() {
        RegisterRequest request = new RegisterRequest(
                "ana@scoala.ro", "Ana Pop", "parola123", "BAD");
        when(inviteTokenService.verify("BAD")).thenThrow(new JwtException("bad invite"));

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOf(JwtException.class);

        verify(registrationService, never()).register(anyString(), anyString(), anyString(), any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void verifyEmailConfirmsAndSavesAccount() {
        User user = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        when(verificationTokenService.verify("TOKEN", TokenPurpose.VERIFY_EMAIL))
                .thenReturn("ana@scoala.ro");
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(user));

        ResponseEntity<Void> response = controller.verifyEmail("TOKEN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailFailsWhenTokenHasNoAccount() {
        when(verificationTokenService.verify("TOKEN", TokenPurpose.VERIFY_EMAIL))
                .thenReturn("fantoma@scoala.ro");
        when(userRepository.findByEmail("fantoma@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.verifyEmail("TOKEN"))
                .isInstanceOf(JwtException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifyEmailPropagatesInvalidToken() {
        when(verificationTokenService.verify(eq("BAD"), any()))
                .thenThrow(new JwtException("bad token"));

        assertThatThrownBy(() -> controller.verifyEmail("BAD"))
                .isInstanceOf(JwtException.class);
    }

    // --- Step 1.6g: local login ---

    @Test
    void loginIssuesCookieAndReturnsUser() {
        User user = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);
        Cookie authCookie = new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, "JWT");
        when(loginService.authenticate("ana@scoala.ro", "parola123")).thenReturn(user);
        when(jwtCookieFactory.create(user)).thenReturn(authCookie);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ResponseEntity<UserDto> response = controller.login(
                new LoginRequest("ana@scoala.ro", "parola123"), servletResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("ana@scoala.ro");
        assertThat(servletResponse.getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNotNull();
    }

    @Test
    void loginPropagatesAuthenticationFailure() {
        when(loginService.authenticate(anyString(), anyString()))
                .thenThrow(AccountNotActiveException.emailNotVerified());
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.login(
                new LoginRequest("ana@scoala.ro", "parola123"), servletResponse))
                .isInstanceOf(AccountNotActiveException.class);

        assertThat(servletResponse.getCookie(JwtCookieSuccessHandler.COOKIE_NAME)).isNull();
    }

    // --- Step 1.6h: forgot / reset password ---

    @Test
    void forgotPasswordAlwaysReturnsOkAndDelegates() {
        ResponseEntity<Void> response =
                controller.forgotPassword(new ForgotPasswordRequest("ana@scoala.ro"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(passwordResetService).requestReset("ana@scoala.ro");
    }

    @Test
    void resetPasswordDelegatesAndReturnsNoContent() {
        ResponseEntity<Void> response =
                controller.resetPassword(new ResetPasswordRequest("RESET", "parolaNoua1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).resetPassword("RESET", "parolaNoua1");
    }
}
