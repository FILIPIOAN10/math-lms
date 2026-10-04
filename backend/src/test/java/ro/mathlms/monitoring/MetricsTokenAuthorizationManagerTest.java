package ro.mathlms.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsTokenAuthorizationManagerTest {

    private static boolean granted(String configuredToken, String authorizationHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        return new MetricsTokenAuthorizationManager(configuredToken)
                .check(() -> null, new RequestAuthorizationContext(request)).isGranted();
    }

    @Test
    void theRightBearerTokenIsGranted() {
        assertThat(granted("s3cret-token", "Bearer s3cret-token")).isTrue();
    }

    @Test
    void aWrongOrMissingTokenIsRefused() {
        assertThat(granted("s3cret-token", "Bearer nope")).isFalse();
        assertThat(granted("s3cret-token", null)).isFalse();
        assertThat(granted("s3cret-token", "s3cret-token")).as("no Bearer prefix").isFalse();
        assertThat(granted("s3cret-token", "Basic czNjcmV0LXRva2Vu")).isFalse();
    }

    @Test
    void aPrefixOrSuperstringOfTheTokenIsNotTheToken() {
        assertThat(granted("s3cret-token", "Bearer s3cret")).isFalse();
        assertThat(granted("s3cret-token", "Bearer s3cret-token-and-more")).isFalse();
    }

    @Test
    void withNoTokenConfiguredNobodyGetsIn() {
        assertThat(granted("", "Bearer ")).isFalse();
        assertThat(granted("", "Bearer anything")).isFalse();
        assertThat(granted(null, "Bearer anything")).isFalse();
        assertThat(granted("   ", "Bearer    ")).isFalse();
    }
}
