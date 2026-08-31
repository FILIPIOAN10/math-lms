package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of(),
                "test-secret-at-least-32-characters-long!!",
                60);
        jwtService = new JwtService(properties);
    }

    @Test
    void generatesTokenWithEmailAndRole() {
        User user = new User("profesor@gmail.com", "Prof Ion", Role.ADMIN);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("profesor@gmail.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void includesStatusClaimForActiveAccount() {
        User user = User.registerGoogle("google-123", "ana@scoala.ro", "Ana Pop", Role.STUDENT);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractStatus(token)).isEqualTo("ACTIVE");
        assertThat(jwtService.extractRole(token)).isEqualTo("STUDENT");
    }

    @Test
    void omitsRoleForNotYetApprovedAccountButKeepsStatus() {
        // A local signup awaiting approval has no role yet — token must still mint.
        User pending = User.registerLocal("ana@scoala.ro", "Ana Pop", "HASH", Role.STUDENT);

        String token = jwtService.generateToken(pending);

        assertThat(jwtService.extractEmail(token)).isEqualTo("ana@scoala.ro");
        assertThat(jwtService.extractStatus(token)).isEqualTo("PENDING_VERIFICATION");
        assertThat(jwtService.extractRole(token)).isNull();
    }

    @Test
    void rejectsTamperedToken() {
        User user = new User("profesor@gmail.com", "Prof Ion", Role.ADMIN);
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.extractEmail(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        User user = new User("profesor@gmail.com", "Prof Ion", Role.ADMIN);
        String token = jwtService.generateToken(user);

        AuthProperties otherProps = new AuthProperties(
                List.of(), List.of(),
                "a-completely-different-secret-key-32ch!!",
                60);
        JwtService otherService = new JwtService(otherProps);

        assertThatThrownBy(() -> otherService.extractEmail(token))
                .isInstanceOf(JwtException.class);
    }
}
