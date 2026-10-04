package ro.mathlms.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final RedisRateLimitService service = mock(RedisRateLimitService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private final RateLimitRule login =
            new RateLimitRule("login", "POST", "/api/auth/login", 5, Duration.ofMinutes(1), RateLimitKeyType.IP);
    private final RateLimitRule upload =
            new RateLimitRule("photo", "POST", "/api/quiz/attempts/*/responses/*/photo", 20, Duration.ofMinutes(1), RateLimitKeyType.USER);

    private RateLimitFilter filter(boolean trustForwardedFor, RateLimitPrincipalResolver resolver) {
        return new RateLimitFilter(service, List.of(login, upload), resolver, trustForwardedFor);
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.7");
        return request;
    }

    @Test
    void aRequestWithoutAMatchingRuleIsNotCounted() throws Exception {
        filter(false, r -> null).doFilter(new MockHttpServletRequest("GET", "/api/classes"), response, chain);

        verifyNoInteractions(service);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void anAllowedRequestPassesAndCarriesTheRemainingBudget() throws Exception {
        when(service.checkLimit(eq("rate_limit:login:ip:10.0.0.7"), eq(login)))
                .thenReturn(new RateLimitResult(true, 2, 3, 55));

        filter(false, r -> null).doFilter(post("/api/auth/login"), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("3");
        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    void anOverTheLimitRequestGets429WithRetryAfterAndNeverReachesTheApp() throws Exception {
        when(service.checkLimit(eq("rate_limit:login:ip:10.0.0.7"), eq(login)))
                .thenReturn(new RateLimitResult(false, 6, 0, 42));

        filter(false, r -> null).doFilter(post("/api/auth/login"), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Prea multe cereri").contains("42");
    }

    @Test
    void theMethodMustMatchToo() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");

        filter(false, r -> null).doFilter(get, response, chain);

        verifyNoInteractions(service);
    }

    @Test
    void aSpoofedForwardedForIsIgnoredUnlessTrusted() throws Exception {
        when(service.checkLimit(any(), any())).thenReturn(new RateLimitResult(true, 1, 4, 60));
        MockHttpServletRequest request = post("/api/auth/login");
        request.addHeader("X-Forwarded-For", "6.6.6.6");

        filter(false, r -> null).doFilter(request, response, chain);

        verify(service).checkLimit(eq("rate_limit:login:ip:10.0.0.7"), any());
    }

    @Test
    void behindATrustedProxyTheFirstForwardedAddressIsTheClient() throws Exception {
        when(service.checkLimit(any(), any())).thenReturn(new RateLimitResult(true, 1, 4, 60));
        MockHttpServletRequest request = post("/api/auth/login");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        filter(true, r -> null).doFilter(request, response, chain);

        verify(service).checkLimit(eq("rate_limit:login:ip:203.0.113.9"), any());
    }

    @Test
    void aUserRuleKeysByTheAuthenticatedUserAndFallsBackToTheIp() throws Exception {
        when(service.checkLimit(any(), any())).thenReturn(new RateLimitResult(true, 1, 19, 60));

        filter(false, r -> "ana@scoala.ro").doFilter(post("/api/quiz/attempts/5/responses/9/photo"), response, chain);
        verify(service).checkLimit(eq("rate_limit:photo:user:ana@scoala.ro"), eq(upload));

        filter(false, r -> null).doFilter(post("/api/quiz/attempts/5/responses/9/photo"), new MockHttpServletResponse(), chain);
        verify(service).checkLimit(eq("rate_limit:photo:ip:10.0.0.7"), eq(upload));
    }
}
