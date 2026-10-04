package ro.mathlms.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Guards {@code /actuator/prometheus}: the request must carry {@code Authorization: Bearer <app.metrics.token>}.
 * The token is compared in constant time, and an unset/blank token means NOBODY gets in — metrics reveal timings,
 * pool sizes and traffic, so the safe default is closed. Nginx additionally never proxies /actuator publicly.
 */
@Component
public class MetricsTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String BEARER = "Bearer ";

    private final byte[] expected;

    public MetricsTokenAuthorizationManager(@Value("${app.metrics.token:}") String token) {
        this.expected = token == null ? new byte[0] : token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        if (expected.length == 0) {
            return new AuthorizationDecision(false);
        }
        String header = context.getRequest().getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return new AuthorizationDecision(false);
        }
        byte[] presented = header.substring(BEARER.length()).strip().getBytes(StandardCharsets.UTF_8);
        return new AuthorizationDecision(MessageDigest.isEqual(presented, expected));
    }
}
