package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Decorates the stored-auth-request repository so that, on the OAuth callback, the
 * invite token stashed by {@link InviteAwareAuthorizationRequestResolver} is copied
 * from the {@link OAuth2AuthorizationRequest} attributes onto the current request.
 * {@code removeAuthorizationRequest} runs in the same request thread as the user
 * service, so {@link CustomOidcUserService} can read it back from there.
 */
public class InviteCapturingAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate;

    public InviteCapturingAuthorizationRequestRepository(
            AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest removed = delegate.removeAuthorizationRequest(request, response);
        if (removed != null) {
            Object invite = removed.getAttribute(InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE);
            if (invite != null) {
                request.setAttribute(InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE, invite);
            }
        }
        return removed;
    }
}
