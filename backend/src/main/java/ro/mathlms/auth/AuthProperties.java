package ro.mathlms.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        List<String> adminEmails,
        List<String> allowedEmails,
        String jwtSecret,
        int jwtExpirationMinutes
) {
}
