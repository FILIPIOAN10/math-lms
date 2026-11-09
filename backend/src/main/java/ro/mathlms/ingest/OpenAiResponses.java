package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;

/**
 * What every OpenAI reader shares (the page reader, the chapter and the mistake suggesters): the client, one call to
 * {@code /responses} with every failure turned into a message for the teacher, and the answer's text. Each caller keeps
 * its own request body, read timeout and reading of the text - the provider's Gemini side has {@link GeminiClient}.
 */
final class OpenAiResponses {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponses.class);

    private OpenAiResponses() {
    }

    /** A client for {@code baseUrl} that gives up connecting after 10 s and reading after {@code readTimeout}. */
    static RestClient client(RestClient.Builder builder, String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * Sends {@code body} and returns OpenAI's raw answer. No key, an HTTP error, no connection or an empty answer is an
     * {@link ExtractionException} worded for the teacher; {@code label} only names the caller in the log.
     */
    static JsonNode post(RestClient restClient, String apiKey, ObjectNode body, String label) throws ExtractionException {
        if (apiKey.isEmpty()) {
            throw new ExtractionException(OpenAiPageExtractor.NO_KEY);
        }
        JsonNode response;
        try {
            response = restClient.post().uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // The body explains a 400 (bad image, unknown model); it never contains the key.
            log.warn("OpenAI ({}) answered {}: {}", label, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ExtractionException(OpenAiPageExtractor.messageFor(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            log.warn("OpenAI ({}) could not be reached: {}", label, e.getMessage());
            throw new ExtractionException(e.getCause() instanceof java.net.SocketTimeoutException
                    ? PageReading.TOO_SLOW : OpenAiPageExtractor.UNAVAILABLE, e);
        }
        if (response == null) {
            throw new ExtractionException(PageReading.UNREADABLE_ANSWER);
        }
        return response;
    }

    /**
     * The answer's text (reasoning items skipped), or {@code null} when it has none; a refusal is an
     * {@link ExtractionException}.
     */
    static String text(JsonNode response, String label) throws ExtractionException {
        String text = null;
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asString())) {
                continue; // reasoning items
            }
            for (JsonNode part : output.path("content")) {
                switch (part.path("type").asString()) {
                    case "refusal" -> {
                        log.warn("OpenAI ({}) refused: {}", label, part.path("refusal").asString());
                        throw new ExtractionException(PageReading.REFUSED);
                    }
                    case "output_text" -> text = part.path("text").asString();
                    default -> { }
                }
            }
        }
        return text;
    }
}
