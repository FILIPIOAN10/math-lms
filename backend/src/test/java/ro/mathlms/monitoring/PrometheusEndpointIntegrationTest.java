package ro.mathlms.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 6.3 on the real app: health stays public, metrics need the token and carry the series the alerts use. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.metrics.token=test-scrape-token")
@AutoConfigureMockMvc
@AutoConfigureObservability // Spring Boot switches metric exporters off in tests unless asked
class PrometheusEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void metricsWithoutOrWithAWrongTokenAreRefused() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOrdinaryLoggedInUserCannotReadMetricsEither() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("prof@scoala.ro").roles("ADMIN")))
                .andExpect(status().isForbidden()); // authenticated, but not the scraper
    }

    @Test
    void theRightTokenGetsTheSeriesTheAlertsQuery() throws Exception {
        // a failed login makes the brute-force counter move; any request feeds the latency histogram
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nimeni@scoala.ro\",\"password\":\"gresita\"}"));

        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer test-scrape-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hikaricp_connections_active")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("security_failed_logins_total")))
                .andExpect(content().string(containsString("outbox_events{application=\"math-lms-backend\",status=\"dead\"}")))
                .andExpect(content().string(containsString("outbox_events{application=\"math-lms-backend\",status=\"pending\"}")));
    }
}
