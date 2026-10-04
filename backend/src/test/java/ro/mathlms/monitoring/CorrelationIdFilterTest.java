package ro.mathlms.monitoring;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /** Runs the filter and returns the id the application code (the chain) saw in the log context. */
    private String run(String incomingHeader, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        if (incomingHeader != null) {
            request.addHeader(CorrelationIdFilter.HEADER, incomingHeader);
        }
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    void aSaneIncomingIdIsKeptSoNginxAndTheBackendShareIt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run("3f2b8c1e9a7d4e5f", response);

        assertThat(seen).isEqualTo("3f2b8c1e9a7d4e5f");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("3f2b8c1e9a7d4e5f");
    }

    @Test
    void withoutAnIncomingIdOneIsGenerated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(null, response);

        assertThat(seen).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seen);
    }

    @Test
    void anUnsafeIncomingIdIsReplacedNotTrusted() throws Exception {
        for (String hostile : new String[]{"short", "has spaces in it 12345", "line\nbreak-forged-entry", "x".repeat(200), "<script>alert(1)</script>"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            String seen = run(hostile, response);

            assertThat(seen).as(hostile).isNotEqualTo(hostile).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void theIdIsRemovedFromTheLogContextAfterTheRequest() throws Exception {
        run("3f2b8c1e9a7d4e5f", new MockHttpServletResponse());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theIdIsRemovedEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        FilterChain failing = (req, res) -> { throw new IllegalStateException("boom"); };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // the failure propagates; what matters is the cleanup below
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
