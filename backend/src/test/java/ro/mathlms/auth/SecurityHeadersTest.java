package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 6.2: every response carries the hardening headers; HSTS only on HTTPS. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void responsesCarryTheHardeningHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")));
    }

    @Test
    void theCspKeepsKatexAndThePhotoPreviewWorking() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy", containsString("style-src 'self' 'unsafe-inline'")))
                .andExpect(header().string("Content-Security-Policy", containsString("img-src 'self' data: blob:")))
                .andExpect(header().string("Content-Security-Policy", containsString("font-src 'self'")));
    }

    @Test
    void hstsIsSentOnlyOverHttps() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mockMvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }

    @Test
    void errorResponsesAreHardenedToo() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
    }
}
