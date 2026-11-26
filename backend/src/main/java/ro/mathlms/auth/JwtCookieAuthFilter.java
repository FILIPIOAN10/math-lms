package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Autowired
    public JwtCookieAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /** Test/local compatibility constructor; production always injects the repository for revocation checks. */
    public JwtCookieAuthFilter(JwtService jwtService) {
        this(jwtService, null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtService.extractEmail(token);
                String role;
                String status;
                if (userRepository != null) {
                    Integer tokenVersion = jwtService.extractTokenVersion(token);
                    User current = userRepository.findByEmail(email)
                            .filter(user -> !user.isErased())
                            .filter(user -> tokenVersion != null && user.getTokenVersion() == tokenVersion)
                            .orElse(null);
                    if (current == null) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    role = current.getRole() == null ? null : current.getRole().name();
                    status = current.getStatus().name();
                } else {
                    role = jwtService.extractRole(token);
                    status = jwtService.extractStatus(token);
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                // A not-yet-approved account has no role yet — only a status.
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
                if (status != null) {
                    authorities.add(new SimpleGrantedAuthority("STATUS_" + status));
                }

                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ignored) {
                // token invalid sau expirat -> ramane neautentificat
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (JwtCookieSuccessHandler.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
