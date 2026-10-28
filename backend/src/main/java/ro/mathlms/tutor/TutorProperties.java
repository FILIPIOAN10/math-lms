package ro.mathlms.tutor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * The tutor service ({@code app.tutor.*}): the help chain over HTTP in {@code tutor/}, which writes hints for a question
 * from the EN chapter pages only. Off unless switched on. {@code token} is the shared secret the service checks;
 * {@code timeout} must stay under nginx's 60 s, because the teacher waits for the answer.
 */
@ConfigurationProperties(prefix = "app.tutor")
public record TutorProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8001") String baseUrl,
        @DefaultValue("") String token,
        @DefaultValue("55s") Duration timeout
) {
}
