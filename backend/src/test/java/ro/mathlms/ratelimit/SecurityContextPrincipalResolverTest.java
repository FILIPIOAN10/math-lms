package ro.mathlms.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityContextPrincipalResolverTest {

    private final SecurityContextPrincipalResolver resolver = new SecurityContextPrincipalResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthenticationMeansAnonymous() {
        assertThat(resolver.resolveUser(new MockHttpServletRequest())).isNull();
    }

    @Test
    void anAuthenticatedUserIsIdentifiedByName() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ana@scoala.ro", null, AuthorityUtils.createAuthorityList("ROLE_STUDENT")));

        assertThat(resolver.resolveUser(new MockHttpServletRequest())).isEqualTo("ana@scoala.ro");
    }

    @Test
    void theAnonymousTokenIsNotAUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(resolver.resolveUser(new MockHttpServletRequest())).isNull();
    }
}
