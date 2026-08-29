package ro.mathlms.auth;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthController controller = new AuthController(userRepository);

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
}
