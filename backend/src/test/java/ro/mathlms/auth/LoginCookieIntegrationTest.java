package ro.mathlms.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real login, end to end: the access cookie leaves as a Set-Cookie header with every attribute it needs, and the
 * browser's copy of it is what authenticates the next request. Logout sends it back empty and expired.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.auth.cookie-secure=true") // as in production (backend/.env turns it off for local http)
@AutoConfigureMockMvc
class LoginCookieIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void theLoginCookieIsHttpOnlySecureLaxAndAuthenticatesTheNextRequest() throws Exception {
        String email = "cookie" + System.nanoTime() + "@scoala.ro";
        User user = User.registerLocal(email, "Elev Cookie", passwordEncoder.encode("parola123"), Role.STUDENT);
        user.verifyEmail();
        user.approve(Role.STUDENT);
        userRepository.save(user);

        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"parola123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        String access = setCookies.stream().filter(c -> c.startsWith(JwtCookieSuccessHandler.COOKIE_NAME + "="))
                .findFirst().orElseThrow();
        assertThat(access).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=");

        Cookie token = login.getResponse().getCookie(JwtCookieSuccessHandler.COOKIE_NAME);
        assertThat(token).isNotNull();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, token.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        String cleared = mockMvc.perform(post("/api/auth/logout")).andReturn().getResponse().getHeaders("Set-Cookie")
                .stream().filter(c -> c.startsWith(JwtCookieSuccessHandler.COOKIE_NAME + "=")).findFirst().orElseThrow();
        assertThat(cleared).startsWith(JwtCookieSuccessHandler.COOKIE_NAME + "=;").contains("Max-Age=0", "Secure");
    }
}
