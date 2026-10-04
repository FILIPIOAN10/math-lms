package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService service;

    @Test
    void rotateIssuesNewTokenAndInvalidatesTheOld() {
        String token = service.createSession("rotate@scoala.ro");

        RefreshTokenService.Rotation rotation = service.rotate(token);

        assertThat(rotation.email()).isEqualTo("rotate@scoala.ro");
        assertThat(rotation.token()).isNotEqualTo(token);
        // The replayed old token must now fail.
        assertThatThrownBy(() -> service.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateAcrossTheSameSessionIsChainable() {
        String t1 = service.createSession("chain@scoala.ro");
        String t2 = service.rotate(t1).token();
        String t3 = service.rotate(t2).token();

        // Still one session for the user (rotation reuses the session, not a new one).
        assertThat(service.listSessions("chain@scoala.ro", t3)).hasSize(1);
    }

    @Test
    void listSessionsFlagsTheCurrentOne() {
        String token = service.createSession("list@scoala.ro");
        service.createSession("list@scoala.ro"); // a second device

        List<DeviceSessionResponse> sessions = service.listSessions("list@scoala.ro", token);

        assertThat(sessions).hasSize(2);
        assertThat(sessions).filteredOn(DeviceSessionResponse::current).hasSize(1);
    }

    @Test
    void revokeSessionRemovesOnlyThatSession() {
        String keep = service.createSession("revoke@scoala.ro");
        service.createSession("revoke@scoala.ro");
        List<DeviceSessionResponse> before = service.listSessions("revoke@scoala.ro", keep);
        String otherId = before.stream().filter(s -> !s.current()).findFirst().orElseThrow().sessionId();

        service.revokeSession("revoke@scoala.ro", otherId);

        assertThat(service.listSessions("revoke@scoala.ro", keep)).hasSize(1);
    }

    @Test
    void revokeSessionRejectsAnotherUsersSession() {
        String token = service.createSession("owner@scoala.ro");
        String ownerSessionId = service.listSessions("owner@scoala.ro", token).get(0).sessionId();

        assertThatThrownBy(() -> service.revokeSession("intruder@scoala.ro", ownerSessionId))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeAllClearsEverySession() {
        String token = service.createSession("all@scoala.ro");
        service.createSession("all@scoala.ro");

        service.revokeAll("all@scoala.ro");

        assertThat(service.listSessions("all@scoala.ro", token)).isEmpty();
        assertThatThrownBy(() -> service.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateRejectsUnknownToken() {
        assertThatThrownBy(() -> service.rotate("not-a-real-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
