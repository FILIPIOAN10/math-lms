package ro.mathlms.ingest;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Signs in to Google as the service account in a JSON key file (OAuth 2.0 JWT bearer flow: an RS256-signed assertion
 * is exchanged for a one-hour access token) and keeps that token until a minute before it expires. The file is read
 * only when a token is needed, never at startup, so the app runs without it for as long as Gemini is not used.
 *
 * <p>Setting: {@code app.ai.gemini.credentials-file} ({@code GEMINI_CREDENTIALS_FILE}), the path of the key that
 * Google Cloud downloads (IAM, Service accounts, Keys). Its private key never appears in a log line.
 */
@Component
public class GoogleAccessTokens implements GeminiClient.AccessToken {

    private static final Logger log = LoggerFactory.getLogger(GoogleAccessTokens.class);

    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    static final Duration LIFETIME = Duration.ofHours(1);
    /** A token this close to expiry is renewed instead of being sent on a 3-minute page read. */
    static final Duration MARGIN = Duration.ofMinutes(1);

    static final String NO_KEY = "Cheia Google (fișierul JSON al contului de serviciu) lipsește din configurarea serverului.";
    static final String BAD_FILE = "Fișierul cu cheia Google nu poate fi citit sau nu este cheia unui cont de serviciu.";
    static final String REJECTED_KEY = "Google a respins cheia contului de serviciu (ștearsă, dezactivată sau nevalidă). "
            + "Verifică-o sau creează alta în Google Cloud.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String credentialsFile;
    private final Clock clock;

    private String token;
    private Instant expiresAt = Instant.MIN;

    @Autowired
    public GoogleAccessTokens(RestClient.Builder builder, ObjectMapper objectMapper,
                              @Value("${app.ai.gemini.credentials-file:}") String credentialsFile, Clock clock) {
        this(builder.clone().requestFactory(timeouts()).build(), objectMapper, credentialsFile, clock);
    }

    /** For tests: a client bound to a mock server and a hand-moved clock. */
    GoogleAccessTokens(RestClient restClient, ObjectMapper objectMapper, String credentialsFile, Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.credentialsFile = credentialsFile == null ? "" : credentialsFile.strip();
        this.clock = clock;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    @Override
    public synchronized String get() throws ExtractionException {
        Instant now = clock.instant();
        if (token != null && now.isBefore(expiresAt.minus(MARGIN))) {
            return token;
        }
        if (credentialsFile.isEmpty()) {
            throw new ExtractionException(NO_KEY);
        }
        ServiceAccount account = read();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("assertion", assertion(account, now));
        JsonNode reply;
        try {
            reply = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // The body is {"error": "invalid_grant", ...}: it never echoes the assertion or the key.
            log.warn("Google token endpoint answered {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExtractionException(e.getStatusCode().is4xxClientError() ? REJECTED_KEY : GeminiClient.UNAVAILABLE, e);
        } catch (ResourceAccessException e) {
            log.warn("Google token endpoint could not be reached: {}", e.getMessage());
            throw new ExtractionException(GeminiClient.UNAVAILABLE, e);
        }
        if (reply == null || !reply.path("access_token").isString()) {
            throw new ExtractionException(GeminiClient.UNAVAILABLE);
        }
        token = reply.get("access_token").asString();
        expiresAt = now.plusSeconds(reply.path("expires_in").asLong(LIFETIME.toSeconds()));
        return token;
    }

    private record ServiceAccount(String email, String keyId, PrivateKey key) {
    }

    private ServiceAccount read() throws ExtractionException {
        try {
            JsonNode json = objectMapper.readTree(Files.readString(Path.of(credentialsFile)));
            if (!"service_account".equals(json.path("type").asString())
                    || !json.path("client_email").isString() || !json.path("private_key").isString()) {
                throw new IllegalArgumentException("not a service-account key");
            }
            String base64 = json.get("private_key").asString()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            String keyId = json.path("private_key_id").isString() ? json.get("private_key_id").asString() : null;
            return new ServiceAccount(json.get("client_email").asString(), keyId, key);
        } catch (Exception e) {
            // The class name only: a parser's message can quote part of the file, and the file holds the private key.
            log.warn("Google key file {} cannot be used ({})", credentialsFile, e.getClass().getSimpleName());
            throw new ExtractionException(BAD_FILE, e);
        }
    }

    private static String assertion(ServiceAccount account, Instant now) {
        var header = Jwts.builder().header().type("JWT");
        if (account.keyId() != null) {
            header.keyId(account.keyId());
        }
        return header.and()
                .issuer(account.email())
                .audience().single(TOKEN_URL) // a plain string: Google rejects an audience array
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(LIFETIME)))
                .signWith(account.key(), Jwts.SIG.RS256)
                .compact();
    }
}
