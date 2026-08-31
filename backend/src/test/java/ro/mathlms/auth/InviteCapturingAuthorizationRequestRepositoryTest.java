package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InviteCapturingAuthorizationRequestRepositoryTest {

    @SuppressWarnings("unchecked")
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate =
            mock(AuthorizationRequestRepository.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final InviteCapturingAuthorizationRequestRepository repository =
            new InviteCapturingAuthorizationRequestRepository(delegate);

    private static OAuth2AuthorizationRequest requestWithInvite(String invite) {
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("http://localhost/login/oauth2/code/google")
                .state("state-123");
        if (invite != null) {
            builder.attributes(attrs ->
                    attrs.put(InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE, invite));
        }
        return builder.build();
    }

    @Test
    void copiesInviteFromStoredRequestOntoTheCurrentRequestOnRemove() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        when(delegate.removeAuthorizationRequest(servletRequest, response))
                .thenReturn(requestWithInvite("INVITE_TOKEN"));

        repository.removeAuthorizationRequest(servletRequest, response);

        assertThat(servletRequest.getAttribute(
                InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE)).isEqualTo("INVITE_TOKEN");
    }

    @Test
    void leavesRequestCleanWhenNoInviteWasStored() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        when(delegate.removeAuthorizationRequest(servletRequest, response))
                .thenReturn(requestWithInvite(null));

        repository.removeAuthorizationRequest(servletRequest, response);

        assertThat(servletRequest.getAttribute(
                InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE)).isNull();
    }

    @Test
    void toleratesNoStoredRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        when(delegate.removeAuthorizationRequest(servletRequest, response)).thenReturn(null);

        assertThat(repository.removeAuthorizationRequest(servletRequest, response)).isNull();
    }
}
