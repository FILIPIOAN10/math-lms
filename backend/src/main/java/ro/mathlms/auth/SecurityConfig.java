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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/verify-email", "/api/auth/login",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Feature endpoints (content, quiz — Faza 2+) require an approved account.
                        // A PENDING account is authenticated (can read /api/auth/me) but not ACTIVE.
                        // Reads of the content hierarchy are for active accounts (students browse);
                        // writes live under /api/admin/** above and need ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/classes/**", "/api/books/**",
                                "/api/chapters/**", "/api/exercises/**").hasAuthority("STATUS_ACTIVE")
                        .requestMatchers("/api/quiz/**", "/api/content/**").hasAuthority("STATUS_ACTIVE")
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
                .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
