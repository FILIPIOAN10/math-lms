package ro.mathlms.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id (Phase 6.3): taken from {@code X-Request-Id} when the caller (nginx)
 * sent a sane one, generated otherwise. It lands in the log context ({@code requestId}, printed on every log
 * line and a field of the structured JSON logs) and is echoed in the response header, so "the user saw an
 * error at 14:03" becomes one id to grep for across nginx and backend logs. Runs first, before security and
 * rate limiting, so even their log lines carry it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** Only short, plain ids are trusted: anything else (newlines, huge values) could forge or flood the logs. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // threads are pooled: never leak one request's id into the next
        }
    }
}
