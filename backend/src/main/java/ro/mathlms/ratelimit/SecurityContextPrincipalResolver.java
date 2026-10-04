package ro.mathlms.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The caller's identity from the Spring Security context. {@link RateLimitFilter} is an ordinary servlet
 * filter, so it runs AFTER the security filter chain, when the JWT cookie has already been turned into an
 * authentication — which is what makes per-user rules possible.
 */
@Component
public class SecurityContextPrincipalResolver implements RateLimitPrincipalResolver {

    @Override
    public String resolveUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
