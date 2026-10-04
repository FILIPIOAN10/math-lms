package ro.mathlms.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/** Gives a USER-keyed rule a stable identity for the caller, or null/blank when the request is anonymous. */
@FunctionalInterface
public interface RateLimitPrincipalResolver {

    String resolveUser(HttpServletRequest request);
}
