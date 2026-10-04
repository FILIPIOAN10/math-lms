package ro.mathlms.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole chain on real Redis: the 6th login attempt from one address is refused with 429 + Retry-After
 * BEFORE the password is even checked, while another address is unaffected.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "rate.limit.enabled=true")
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static final AtomicInteger NEXT_HOST = new AtomicInteger(10);

    @Autowired
    private MockMvc mockMvc;

    /** Each test gets its own client address so the Redis counters of earlier tests cannot interfere. */
    private static RequestPostProcessor fromNewAddress() {
        String ip = "10.77.0." + NEXT_HOST.getAndIncrement();
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void wrongLogin(RequestPostProcessor from, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/auth/login").with(from)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nimeni@scoala.ro\",\"password\":\"gresita\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void theSixthLoginAttemptInAMinuteIsRefusedWith429() throws Exception {
        RequestPostProcessor attacker = fromNewAddress();

        for (int i = 0; i < 5; i++) {
            wrongLogin(attacker, 401); // the login itself runs and says "wrong credentials"
        }

        mockMvc.perform(post("/api/auth/login").with(attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nimeni@scoala.ro\",\"password\":\"gresita\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void anotherAddressKeepsItsOwnBudget() throws Exception {
        RequestPostProcessor busy = fromNewAddress();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/auth/login").with(busy)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"x@scoala.ro\",\"password\":\"y\"}"));
        }

        wrongLogin(fromNewAddress(), 401);
    }

    @Test
    void endpointsWithoutARuleAreNeverLimited() throws Exception {
        RequestPostProcessor client = fromNewAddress();

        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/api/auth/logout").with(client)).andExpect(status().isNoContent());
        }
    }
}
