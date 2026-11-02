package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import ro.mathlms.TestcontainersConfiguration;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The handler sits in the real login filter: Google's answer comes back through the whole chain to the login page. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GoogleLoginFailureIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Value("${app.frontend.base-url}") private String frontendBaseUrl;

    @Test
    void cancellingOnGooglesPageEndsOnTheLoginPageWithTheReason() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String toGoogle = mockMvc.perform(get("/oauth2/authorization/google").session(session))
                .andReturn().getResponse().getRedirectedUrl();
        String state = UriUtils.decode(UriComponentsBuilder.fromUriString(toGoogle).build().getQueryParams().getFirst("state"),
                StandardCharsets.UTF_8);

        String back = mockMvc.perform(get("/login/oauth2/code/google").session(session)
                        .param("error", "access_denied").param("state", state))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(back).isEqualTo(frontendBaseUrl + "/login?error=access_denied");
    }

    @Test
    void anAnswerThatMatchesNoSignInEndsOnTheLoginPageToo() throws Exception {
        String back = mockMvc.perform(get("/login/oauth2/code/google").param("code", "x").param("state", "necunoscut"))
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(back).isEqualTo(frontendBaseUrl + "/login?error=google_failed");
    }
}
