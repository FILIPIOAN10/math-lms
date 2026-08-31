package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InviteAwareAuthorizationRequestResolverTest {

    private final OAuth2AuthorizationRequestResolver delegate =
            mock(OAuth2AuthorizationRequestResolver.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final InviteAwareAuthorizationRequestResolver resolver =
            new InviteAwareAuthorizationRequestResolver(delegate);

    private static OAuth2AuthorizationRequest baseRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost/login/oauth2/code/google")
                .state("state-123")
                .build();
    }

    @Test
    void stashesInviteTokenInAttributesWhenPresent() {
        when(delegate.resolve(request)).thenReturn(baseRequest());
        when(request.getParameter("invite")).thenReturn("INVITE_TOKEN");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result.getAttributes())
                .containsEntry(InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE, "INVITE_TOKEN");
    }

    @Test
    void doesNotLeakInviteIntoParametersSentToGoogle() {
        when(delegate.resolve(request)).thenReturn(baseRequest());
        when(request.getParameter("invite")).thenReturn("INVITE_TOKEN");

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result.getAdditionalParameters())
                .doesNotContainKey(InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE);
        assertThat(result.getAdditionalParameters()).doesNotContainValue("INVITE_TOKEN");
    }

    @Test
    void leavesRequestUntouchedWhenNoInvite() {
        OAuth2AuthorizationRequest base = baseRequest();
        when(delegate.resolve(request)).thenReturn(base);
        when(request.getParameter("invite")).thenReturn(null);

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isSameAs(base);
    }

    @Test
    void returnsNullWhenDelegateDoesNotResolveAnAuthorizationRequest() {
        when(delegate.resolve(request)).thenReturn(null);

        assertThat(resolver.resolve(request)).isNull();
    }
}
