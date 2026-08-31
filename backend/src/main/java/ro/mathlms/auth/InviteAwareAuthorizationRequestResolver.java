package ro.mathlms.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Wraps the default resolver so an {@code ?invite=<token>} on the authorization
 * request is stashed in the {@link OAuth2AuthorizationRequest} <em>attributes</em>
 * (kept server-side, correlated by {@code state}) — never in the parameters sent to
 * Google. It is read back on the callback to derive the invited role.
 */
public class InviteAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** Attribute key under which the invite token travels with the auth request. */
    public static final String INVITE_ATTRIBUTE = "invite_token";

    /** Query parameter the frontend appends to the Google authorization URL. */
    static final String INVITE_PARAM = "invite";

    private final OAuth2AuthorizationRequestResolver delegate;

    public InviteAwareAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withInvite(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withInvite(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest withInvite(OAuth2AuthorizationRequest resolved,
                                                  HttpServletRequest request) {
        if (resolved == null) {
            return null; // not an authorization request
        }
        String invite = request.getParameter(INVITE_PARAM);
        if (invite == null || invite.isBlank()) {
            return resolved;
        }
        return OAuth2AuthorizationRequest.from(resolved)
                .attributes(attrs -> attrs.put(INVITE_ATTRIBUTE, invite))
                .build();
    }
}
