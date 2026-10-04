package ro.mathlms.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * No inline script, no foreign origins. {@code style-src 'unsafe-inline'} because KaTeX positions formulas with
     * inline styles; {@code img-src} allows data:/blob: for the resized solution photo preview.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data: blob:; "
            + "style-src 'self' 'unsafe-inline'; font-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    /** BCrypt hashing for local (email/password) accounts. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            CustomOidcUserService customOidcUserService,
                                            JwtCookieSuccessHandler jwtCookieSuccessHandler,
                                            JwtCookieAuthFilter jwtCookieAuthFilter,
                                            ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        OAuth2AuthorizationRequestResolver inviteAwareResolver =
                new InviteAwareAuthorizationRequestResolver(
                        new DefaultOAuth2AuthorizationRequestResolver(
                                clientRegistrationRepository, "/oauth2/authorization"));
        InviteCapturingAuthorizationRequestRepository inviteCapturingRepository =
                new InviteCapturingAuthorizationRequestRepository(
                        new HttpSessionOAuth2AuthorizationRequestRepository());

        // Cookie double-submit CSRF for the browser SPA: the token rides in a JS-readable
        // XSRF-TOKEN cookie and must be echoed as the X-XSRF-TOKEN header on state-changing
        // requests. The auth/OAuth2/public endpoints are exempt — they are first-contact (no
        // cookie yet) or gated by their own single-use token.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // JwtCookieAuthFilter re-authenticates every request, and with STATELESS sessions
                        // SessionManagementFilter treats each one as a fresh login. The default
                        // CsrfAuthenticationStrategy would then rotate the token on every request — deleting
                        // the XSRF-TOKEN cookie on each read, so the SPA's next write carried no token (403).
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .ignoringRequestMatchers("/api/auth/**", "/api/public/**",
                                "/oauth2/**", "/login/**"))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Response hardening (Phase 6.2). The API only returns JSON/images, so the CSP is strict. The SPA's
                // own HTML is served by nginx in production, which sends the same set (see deploy/nginx.conf).
                // HSTS is only emitted on HTTPS requests; X-Content-Type-Options: nosniff is on by default.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .frameOptions(frame -> frame.deny())
                        .permissionsPolicyHeader(permissions -> permissions.policy("geolocation=(), microphone=(), payment=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Boot renders denials by forwarding to /error. That ERROR dispatch re-enters
                        // this chain unauthenticated (OncePerRequestFilter skips error dispatches, so
                        // JwtCookieAuthFilter never runs), so gating it would turn every 403 into a
                        // 401 and hide the real reason a request was refused.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/verify-email", "/api/auth/login",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                // refresh runs on the refresh cookie (access token may be expired);
                                // logout only clears cookies + revokes the presented refresh token.
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // GDPR erasure is confirmed via an emailed single-purpose token, no session.
                        .requestMatchers("/api/public/gdpr/erase/confirm").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Feature endpoints (content, quiz — Faza 2+) require an approved account.
                        // A PENDING account is authenticated (can read /api/auth/me) but not ACTIVE.
                        // Reads of the content hierarchy are for active accounts (students browse);
                        // writes live under /api/admin/** above and need ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/classes/**", "/api/books/**",
                                "/api/chapters/**", "/api/exercises/**").hasAuthority("STATUS_ACTIVE")
                        .requestMatchers("/api/quiz/**", "/api/content/**", "/api/me/**", "/api/parent/**").hasAuthority("STATUS_ACTIVE")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authz -> authz
                                .authorizationRequestResolver(inviteAwareResolver)
                                .authorizationRequestRepository(inviteCapturingRepository))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(customOidcUserService))
                        .successHandler(jwtCookieSuccessHandler))
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
