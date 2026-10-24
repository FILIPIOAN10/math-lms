package ro.mathlms.teaching;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ro.mathlms.TestcontainersConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A TEACHER passes the {@code /api/admin/**} URL rule, so that rule alone protects nothing any more: every handler there
 * must say who may call it with its own {@link PreAuthorize} (on the method or the class). A new admin endpoint without
 * one fails here instead of quietly opening to every teacher.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminEndpointGuardTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    void everyAdminEndpointDeclaresWhoMayCallIt() {
        List<String> unguarded = new ArrayList<>();
        int adminEndpoints = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            boolean admin = entry.getKey().getPatternValues().stream().anyMatch(p -> p.startsWith("/api/admin"));
            if (!admin) {
                continue;
            }
            adminEndpoints++;
            HandlerMethod handler = entry.getValue();
            boolean guarded = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
            if (!guarded) {
                unguarded.add(entry.getKey().getPatternValues() + " -> " + handler.getShortLogMessage());
            }
        }
        assertThat(adminEndpoints).isGreaterThan(50);
        assertThat(unguarded).isEmpty();
    }
}
