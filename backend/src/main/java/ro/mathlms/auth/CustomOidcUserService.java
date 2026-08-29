package ro.mathlms.auth;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import ro.mathlms.user.User;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserProvisioningService provisioningService;

    public CustomOidcUserService(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName();

        User user = provisioningService.provision(email, fullName);

        return new AppOidcUser(oidcUser, user);
    }
}
