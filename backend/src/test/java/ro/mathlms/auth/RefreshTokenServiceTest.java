package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redis;

    private final SettableClock clock = new SettableClock(Instant.parse("2026-11-28T08:00:00Z"));

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

    // --- a session nobody uses for an hour closes itself ---

    @Test
    void aSessionLeftAloneLongerThanTheIdleTimeoutIsClosed() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));
        String email = createAccount("idle");
        String token = idle.createSession(email);

        clock.advance(Duration.ofMinutes(61));

        assertThatThrownBy(() -> idle.rotate(token)).isInstanceOf(InvalidRefreshTokenException.class);
        // Closed for good, not just refused once: it is gone from the device list too.
        assertThat(idle.listSessions(email, token)).isEmpty();
    }

    @Test
    void aSessionUsedWithinTheHourStaysOpen() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));
        String email = createAccount("within");
        String token = idle.createSession(email);

        clock.advance(Duration.ofMinutes(59));

        assertThat(idle.rotate(token).email()).isEqualTo(email);
    }

    @Test
    void eachActivityPingStartsTheHourAgain() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));
        String email = createAccount("ping");
        String token = idle.createSession(email);

        clock.advance(Duration.ofMinutes(50));
        idle.touch(token);
        clock.advance(Duration.ofMinutes(50));
        idle.touch(token);
        clock.advance(Duration.ofMinutes(50));

        // 150 minutes since the sign-in, but never an hour without a sign of life.
        assertThat(idle.rotate(token).email()).isEqualTo(email);
    }

    @Test
    void aRefreshAlsoCountsAsActivity() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));
        String email = createAccount("rotation");
        String token = idle.createSession(email);

        clock.advance(Duration.ofMinutes(50));
        String next = idle.rotate(token).token();
        clock.advance(Duration.ofMinutes(50));

        assertThat(idle.rotate(next).email()).isEqualTo(email);
    }

    @Test
    void anActivityPingCannotReviveAnIdleSession() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));
        String email = createAccount("revive");
        String token = idle.createSession(email);

        clock.advance(Duration.ofMinutes(61));

        assertThatThrownBy(() -> idle.touch(token)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(idle.listSessions(email, token)).isEmpty();
    }

    @Test
    void anActivityPingNeedsARealSession() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));

        assertThatThrownBy(() -> idle.touch(null)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> idle.touch("not-a-real-token")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void redisForgetsAnIdleSessionByItselfAfterTheTimeout() {
        RefreshTokenService idle = withClock(Duration.ofDays(7), Duration.ofMinutes(60));
        String email = createAccount("ttl");
        String token = idle.createSession(email);
        String sessionId = idle.listSessions(email, token).get(0).sessionId();

        assertThat(redis.getExpire("refresh:session:" + sessionId, TimeUnit.SECONDS)).isBetween(3500L, 3600L);
        assertThat(redis.getExpire("refresh:token:" + token, TimeUnit.SECONDS)).isBetween(3500L, 3600L);

        String next = idle.rotate(token).token();
        assertThat(redis.getExpire("refresh:token:" + next, TimeUnit.SECONDS)).isBetween(3500L, 3600L);
    }

    @Test
    void evenABusySessionEndsAtTheAbsoluteLifetime() {
        RefreshTokenService idle = withClock(Duration.ofHours(2), Duration.ofMinutes(60));
        String email = createAccount("absolute");
        String token = idle.createSession(email);

        clock.advance(Duration.ofMinutes(50));
        idle.touch(token);
        clock.advance(Duration.ofMinutes(50));
        idle.touch(token);
        clock.advance(Duration.ofMinutes(50));

        assertThatThrownBy(() -> idle.rotate(token)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void anAccessCookieMayNotOutliveAnIdleSession() {
        assertThatThrownBy(() -> RefreshTokenService.requireAccessWithinIdle(Duration.ofMinutes(61), Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_EXPIRATION_MINUTES")
                .hasMessageContaining("IDLE_TIMEOUT_MINUTES");
        assertThatCode(() -> RefreshTokenService.requireAccessWithinIdle(Duration.ofMinutes(60), Duration.ofMinutes(60)))
                .doesNotThrowAnyException();
    }

    @Test
    void theAppNamesItsIdleTimeout() {
        assertThat(service.idleTimeout()).isEqualTo(Duration.ofMinutes(60));
    }

    private RefreshTokenService withClock(Duration absoluteLifetime, Duration idleTimeout) {
        return new RefreshTokenService(redis, userRepository, clock, false, absoluteLifetime, idleTimeout);
    }

    /** A clock the test moves by hand: an hour of idleness without waiting an hour. */
    private static final class SettableClock extends Clock {
        private Instant now;

        SettableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private String createAccount(String label) {
        String id = UUID.randomUUID().toString();
        String email = label + "-" + id + "@scoala.ro";
        userRepository.save(User.registerGoogle("g-" + id, email, "Test Student", Role.STUDENT));
        return email;
    }
}
