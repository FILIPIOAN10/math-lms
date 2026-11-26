package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private UserRepository userRepository;

    @Test
    void rotateIssuesNewTokenAndInvalidatesTheOld() {
        String email = createAccount("rotate");
        String token = service.createSession(email);

        RefreshTokenService.Rotation rotation = service.rotate(token);

        assertThat(rotation.email()).isEqualTo(email);
        assertThat(rotation.token()).isNotEqualTo(token);
        // The replayed old token must now fail.
        assertThatThrownBy(() -> service.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateAcrossTheSameSessionIsChainable() {
        String email = createAccount("chain");
        String t1 = service.createSession(email);
        String t2 = service.rotate(t1).token();
        String t3 = service.rotate(t2).token();

        // Still one session for the user (rotation reuses the session, not a new one).
        assertThat(service.listSessions(email, t3)).hasSize(1);
    }

    @Test
    void listSessionsFlagsTheCurrentOne() {
        String email = createAccount("list");
        String token = service.createSession(email);
        service.createSession(email); // a second device

        List<DeviceSessionResponse> sessions = service.listSessions(email, token);

        assertThat(sessions).hasSize(2);
        assertThat(sessions).filteredOn(DeviceSessionResponse::current).hasSize(1);
    }

    @Test
    void revokeSessionRemovesOnlyThatSession() {
        String email = createAccount("revoke");
        String keep = service.createSession(email);
        service.createSession(email);
        List<DeviceSessionResponse> before = service.listSessions(email, keep);
        String otherId = before.stream().filter(s -> !s.current()).findFirst().orElseThrow().sessionId();

        service.revokeSession(email, otherId);

        assertThat(service.listSessions(email, keep)).hasSize(1);
    }

    @Test
    void revokeSessionRejectsAnotherUsersSession() {
        String owner = createAccount("owner");
        String token = service.createSession(owner);
        String ownerSessionId = service.listSessions(owner, token).get(0).sessionId();

        assertThatThrownBy(() -> service.revokeSession("intruder-" + UUID.randomUUID() + "@scoala.ro", ownerSessionId))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeAllClearsEverySession() {
        String email = createAccount("all");
        String token = service.createSession(email);
        service.createSession(email);

        service.revokeAll(email);

        assertThat(service.listSessions(email, token)).isEmpty();
        assertThatThrownBy(() -> service.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsUnknownToken() {
        assertThatThrownBy(() -> service.rotate("not-a-real-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    private String createAccount(String label) {
        String id = UUID.randomUUID().toString();
        String email = label + "-" + id + "@scoala.ro";
        userRepository.save(User.registerGoogle("g-" + id, email, "Test Student", Role.STUDENT));
        return email;
    }
}
