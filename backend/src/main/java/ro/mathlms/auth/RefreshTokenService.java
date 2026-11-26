package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed refresh tokens with per-device session tracking and rotation. Identity is the user's
 * email (the authenticated principal). Each login opens a <em>session</em> addressed by an opaque
 * {@code sessionId}; the refresh token rotates on every {@code /refresh} (old token deleted, new one
 * issued for the same session) so a replayed token fails. The session is the public handle for
 * listing and revoking — the refresh token never leaves Redis except in its own HttpOnly cookie.
 *
 * <p>Redis layout (all TTL-bounded):
 * <ul>
 *   <li>{@code refresh:token:<token>} → {@code sessionId} (validate a presented token)</li>
 *   <li>{@code refresh:session:<sessionId>} → hash {email, token, device, ip, createdAt, lastUsedAt}</li>
 *   <li>{@code refresh:user:<email>} → set of that user's sessionIds</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public static final String REFRESH_COOKIE_NAME = "MATHLMS_REFRESH";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final String TOKEN_PREFIX = "refresh:token:";
    private static final String SESSION_PREFIX = "refresh:session:";
    private static final String USER_SESSIONS_PREFIX = "refresh:user:";

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final boolean cookieSecure;
    private final Duration ttl;

    @Autowired
    public RefreshTokenService(StringRedisTemplate redis, UserRepository userRepository,
                               @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
                               @Value("${app.auth.refresh-expiration-days:7}") long refreshExpirationDays) {
        this.redis = redis;
        this.userRepository = userRepository;
        this.cookieSecure = cookieSecure;
        this.ttl = Duration.ofDays(refreshExpirationDays);
    }

    /** Test compatibility constructor; production injects the repository for version checks. */
    public RefreshTokenService(StringRedisTemplate redis,
                               @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
                               @Value("${app.auth.refresh-expiration-days:7}") long refreshExpirationDays) {
        this(redis, null, cookieSecure, refreshExpirationDays);
    }

    /** The email + freshly issued token produced by a rotation. */
    public record Rotation(String email, String token) {}

    /** Opens a new session for the user and returns its refresh token (for the cookie). */
    public String createSession(String email) {
        User user = userRepository == null ? null : userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRefreshTokenException("No account for this session"));
        int tokenVersion = user == null ? 0 : user.getTokenVersion();
        String sessionId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        RequestInfo info = extractRequestInfo();

        redis.opsForValue().set(TOKEN_PREFIX + token, sessionId, ttl);

        Map<String, String> session = new HashMap<>();
        session.put("email", email);
        session.put("tokenVersion", String.valueOf(tokenVersion));
        session.put("token", token);
        session.put("device", info.device());
        session.put("ip", info.ip());
        session.put("createdAt", String.valueOf(now));
        session.put("lastUsedAt", String.valueOf(now));
        redis.opsForHash().putAll(SESSION_PREFIX + sessionId, session);
        redis.expire(SESSION_PREFIX + sessionId, ttl);

        redis.opsForSet().add(USER_SESSIONS_PREFIX + email, sessionId);
        redis.expire(USER_SESSIONS_PREFIX + email, ttl);
        return token;
    }

    /** Rotates the presented token within its session and returns the owner + the new token. */
    public Rotation rotate(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }
        String sessionId = redis.opsForValue().get(TOKEN_PREFIX + oldToken);
        if (sessionId == null) {
            throw new InvalidRefreshTokenException("Invalid or expired refresh token");
        }
        String sessionKey = SESSION_PREFIX + sessionId;
        String email = (String) redis.opsForHash().get(sessionKey, "email");
        if (email == null) {
            throw new InvalidRefreshTokenException("Invalid or expired refresh token");
        }
        if (userRepository != null) {
            Object storedVersion = redis.opsForHash().get(sessionKey, "tokenVersion");
            long sessionVersion = storedVersion == null ? 0 : Long.parseLong(String.valueOf(storedVersion));
            User user = userRepository.findByEmail(email)
                    .filter(candidate -> !candidate.isErased())
                    .filter(candidate -> candidate.getTokenVersion() == sessionVersion)
                    .orElse(null);
            if (user == null) {
                revokeSession(email, sessionId);
                throw new InvalidRefreshTokenException("Invalid or expired refresh token");
            }
        }
        String newToken = UUID.randomUUID().toString();
        redis.delete(TOKEN_PREFIX + oldToken);
        redis.opsForValue().set(TOKEN_PREFIX + newToken, sessionId, ttl);
        redis.opsForHash().put(sessionKey, "token", newToken);
        redis.opsForHash().put(sessionKey, "lastUsedAt", String.valueOf(System.currentTimeMillis()));
        redis.expire(sessionKey, ttl);
        redis.expire(USER_SESSIONS_PREFIX + email, ttl);
        return new Rotation(email, newToken);
    }

    /** Sessions of a user, newest activity first. The session matching {@code currentToken} is flagged. */
    public List<DeviceSessionResponse> listSessions(String email, String currentToken) {
        String currentSessionId = currentToken == null ? null : redis.opsForValue().get(TOKEN_PREFIX + currentToken);
        Set<String> ids = redis.opsForSet().members(USER_SESSIONS_PREFIX + email);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<DeviceSessionResponse> out = new ArrayList<>();
        for (String sessionId : ids) {
            Map<Object, Object> e = redis.opsForHash().entries(SESSION_PREFIX + sessionId);
            if (e.isEmpty()) {
                redis.opsForSet().remove(USER_SESSIONS_PREFIX + email, sessionId);
                continue;
            }
            out.add(new DeviceSessionResponse(
                    sessionId,
                    String.valueOf(e.get("device")),
                    String.valueOf(e.get("ip")),
                    Long.parseLong(String.valueOf(e.get("createdAt"))),
                    Long.parseLong(String.valueOf(e.get("lastUsedAt"))),
                    sessionId.equals(currentSessionId)));
        }
        out.sort(Comparator.comparingLong(DeviceSessionResponse::lastUsedAt).reversed());
        return out;
    }

    /** Revokes one session by its opaque id; only the owner may revoke it. */
    public void revokeSession(String email, String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        Object owner = redis.opsForHash().get(sessionKey, "email");
        if (owner == null || !email.equals(owner)) {
            throw new InvalidRefreshTokenException("Session not found");
        }
        Object token = redis.opsForHash().get(sessionKey, "token");
        if (token != null) {
            redis.delete(TOKEN_PREFIX + token);
        }
        redis.delete(sessionKey);
        redis.opsForSet().remove(USER_SESSIONS_PREFIX + email, sessionId);
    }

    /** Revokes whatever session a presented token belongs to (logout of the current device). */
    public void revokeByToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String sessionId = redis.opsForValue().get(TOKEN_PREFIX + token);
        redis.delete(TOKEN_PREFIX + token);
        if (sessionId == null) {
            return;
        }
        Object email = redis.opsForHash().get(SESSION_PREFIX + sessionId, "email");
        redis.delete(SESSION_PREFIX + sessionId);
        if (email != null) {
            redis.opsForSet().remove(USER_SESSIONS_PREFIX + email, sessionId);
        }
    }

    /** Invalidates every session (logout everywhere; also used by reset and GDPR erasure). */
    public void revokeAll(String email) {
        // The database version invalidates old access JWTs and refresh sessions even if Redis cleanup is interrupted.
        if (userRepository != null) {
            userRepository.findByEmail(email).ifPresent(user -> {
                user.invalidateAccessTokens();
                userRepository.save(user);
            });
        }
        try {
            String userKey = USER_SESSIONS_PREFIX + email;
            Set<String> ids = redis.opsForSet().members(userKey);
            if (ids != null) {
                for (String sessionId : ids) {
                    Object token = redis.opsForHash().get(SESSION_PREFIX + sessionId, "token");
                    if (token != null) {
                        redis.delete(TOKEN_PREFIX + token);
                    }
                    redis.delete(SESSION_PREFIX + sessionId);
                }
            }
            redis.delete(userKey);
        } catch (RuntimeException e) {
            // The DB version already invalidated the credentials; Redis keys expire naturally if cleanup is unavailable.
            log.warn("Refresh-session cleanup failed after version revocation ({})", e.getClass().getSimpleName());
        }
    }

    // --- cookies ---

    public String readRefreshCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, REFRESH_COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }

    public ResponseCookie refreshCookie(String token) {
        return baseCookie(token).maxAge(ttl).build();
    }

    public ResponseCookie cleanRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(REFRESH_COOKIE_PATH)
                .sameSite("Lax");
    }

    private record RequestInfo(String device, String ip) {}

    private RequestInfo extractRequestInfo() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return new RequestInfo("Unknown device", "unknown");
        }
        HttpServletRequest request = attrs.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor != null && !forwardedFor.isBlank()
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        return new RequestInfo(deviceOf(request.getHeader("User-Agent")), ip == null ? "unknown" : ip);
    }

    private static String deviceOf(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        String type = (ua.contains("android") || ua.contains("iphone") || ua.contains("ipod")) ? "Mobile"
                : (ua.contains("ipad") || ua.contains("tablet")) ? "Tablet" : "Desktop";
        String browser = ua.contains("edg") ? "Edge"
                : (ua.contains("opr") || ua.contains("opera")) ? "Opera"
                : ua.contains("chrome") ? "Chrome"
                : ua.contains("firefox") ? "Firefox"
                : ua.contains("safari") ? "Safari" : "Browser";
        return type + " - " + browser;
    }
}
