package ro.mathlms.ingest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The service-account sign-in against a mock token endpoint: what is signed, how long a token is kept, what fails. */
class GoogleAccessTokensTest {

    private static final String EMAIL = "math-lms-reader@proj.iam.gserviceaccount.com";

    @TempDir
    Path dir;

    private KeyPair keys;
    private Path keyFile;
    private MutableClock clock;
    private MockRestServiceServer server;
    private RestClient restClient;
    private final List<String> forms = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        // Generated above for this run only: the PEM wraps a throwaway key, never a real one.
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded());
        String pem = String.join("\n", "-----BEGIN PRIVATE KEY-----", body, "-----END PRIVATE KEY-----", "");
        keyFile = dir.resolve("key.json");
        Files.writeString(keyFile, new ObjectMapper().writeValueAsString(Map.of(
                "type", "service_account", "project_id", "proj", "client_email", EMAIL, "private_key", pem)));
        clock = new MutableClock(Instant.parse("2026-10-20T12:00:00Z"));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private GoogleAccessTokens tokens(String file) {
        return new GoogleAccessTokens(restClient, new ObjectMapper(), file, clock);
    }

    private void expectToken(String token) {
        server.expect(once(), requestTo(GoogleAccessTokens.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> forms.add(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"access_token\": \"" + token + "\", \"expires_in\": 3599, \"token_type\": \"Bearer\"}",
                        MediaType.APPLICATION_JSON));
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new java.util.HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            fields.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return fields;
    }

    @Test
    void signsAnAssertionForTheServiceAccountAndReturnsTheToken() throws Exception {
        expectToken("ya29.one");

        String token = tokens(keyFile.toString()).get();

        server.verify();
        assertThat(token).isEqualTo("ya29.one");
        Map<String, String> form = form(forms.getFirst());
        assertThat(form.get("grant_type")).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
        Claims claims = Jwts.parser().verifyWith(keys.getPublic()).clock(() -> Date.from(clock.instant())).build()
                .parseSignedClaims(form.get("assertion")).getPayload();
        assertThat(claims.getIssuer()).isEqualTo(EMAIL);
        assertThat(claims.getAudience()).containsExactly(GoogleAccessTokens.TOKEN_URL);
        // Google rejects an audience array ("Failed audience check"): it must be one plain string.
        String payload = new String(Base64.getUrlDecoder().decode(form.get("assertion").split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(new ObjectMapper().readTree(payload).get("aud").isString()).isTrue();
        assertThat(claims.get("scope", String.class)).isEqualTo(GoogleAccessTokens.SCOPE);
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(clock.instant());
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofHours(1));
    }

    @Test
    void keepsTheTokenUntilShortlyBeforeItExpires() throws Exception {
        GoogleAccessTokens tokens = tokens(keyFile.toString());
        expectToken("ya29.one");
        assertThat(tokens.get()).isEqualTo("ya29.one");
        clock.advance(Duration.ofMinutes(50));
        assertThat(tokens.get()).isEqualTo("ya29.one");
        server.verify();

        server.reset();
        expectToken("ya29.two");
        clock.advance(Duration.ofMinutes(9)); // 59 min: within the last minute of 3599 s
        assertThat(tokens.get()).isEqualTo("ya29.two");
        server.verify();
    }

    @Test
    void noFileConfiguredMeansNoCall() {
        assertThatThrownBy(() -> tokens(" ").get()).hasMessage(GoogleAccessTokens.NO_KEY);
        server.verify();
    }

    @Test
    void aFileThatIsMissingOrNotAServiceAccountKeyFailsPlainly() throws Exception {
        assertThatThrownBy(() -> tokens(dir.resolve("missing.json").toString()).get())
                .hasMessage(GoogleAccessTokens.BAD_FILE);

        Path notAKey = dir.resolve("other.json");
        Files.writeString(notAKey, "{\"type\": \"authorized_user\"}");
        assertThatThrownBy(() -> tokens(notAKey.toString()).get()).hasMessage(GoogleAccessTokens.BAD_FILE);
        server.verify();
    }

    @Test
    void aKeyGoogleNoLongerAcceptsIsReportedAsSuch() {
        server.expect(requestTo(GoogleAccessTokens.TOKEN_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"invalid_grant\", \"error_description\": \"Invalid JWT Signature.\"}"));

        assertThatThrownBy(() -> tokens(keyFile.toString()).get()).hasMessage(GoogleAccessTokens.REJECTED_KEY);
    }

    @Test
    void googleUnavailableIsNotBlamedOnTheKey() {
        server.expect(requestTo(GoogleAccessTokens.TOKEN_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> tokens(keyFile.toString()).get()).hasMessage(GeminiClient.UNAVAILABLE);
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
