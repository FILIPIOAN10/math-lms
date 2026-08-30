package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

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
    private final AuthController controller = new AuthController(
            userRepository, registrationService, emailService, verificationTokenService);

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
    void registerCreatesAccountAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest(
                "ana@scoala.ro", "Ana Pop", "parola123", Role.STUDENT);
        User created = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);
        when(registrationService.register("ana@scoala.ro", "Ana Pop", "parola123", Role.STUDENT))
                .thenReturn(created);
        when(verificationTokenService.generate("ana@scoala.ro", TokenPurpose.VERIFY_EMAIL))
                .thenReturn("TOKEN");

        ResponseEntity<Void> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(emailService).sendVerificationEmail("ana@scoala.ro", "TOKEN");
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
}
