package ro.mathlms.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fake inbox the browser tests read their emails from: Mailpit ({@code docker compose --profile e2e up -d mailpit}),
 * with the backend sending to it ({@code SMTP_HOST=localhost}, {@code SMTP_PORT=1025}; docs/TESTING.md, E2E). Every
 * test writes to its own fresh address, so an email from an earlier run never answers for this one.
 */
final class Mailbox {

    static final String URL = System.getProperty("e2e.mailpitUrl", "http://localhost:8025");
    private static final Duration ARRIVAL = Duration.ofSeconds(20);
    private static final Pattern LINK = Pattern.compile("https?://\\S+");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private Mailbox() {
    }

    static boolean isUp() {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(URL + "/api/v1/info")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The link to {@code path} (e.g. "/verify-email") in the newest email to {@code to}, once it has arrived. */
    static String linkTo(String to, String path) {
        return linkTo(to, path, 0);
    }

    /** The same, in an email that came after the first {@code earlier} ones with such a link. */
    static String linkTo(String to, String path, int earlier) {
        Instant giveUp = Instant.now().plus(ARRIVAL);
        while (Instant.now().isBefore(giveUp)) {
            List<String> links = links(to, path);
            if (links.size() > earlier) {
                return links.getFirst();
            }
            pause();
        }
        throw new AssertionError("No email to " + to + " with a link to " + path + " in Mailpit after "
                + ARRIVAL.toSeconds() + " s. Is the backend sending to Mailpit (SMTP_HOST=localhost, SMTP_PORT=1025)?");
    }

    /** How many emails to {@code to} carry a link to {@code path}. */
    static int count(String to, String path) {
        return links(to, path).size();
    }

    /** Newest first, as Mailpit lists them: one link per email. */
    private static List<String> links(String to, String path) {
        JsonNode found = get("/api/v1/search?query=" + URLEncoder.encode("to:\"" + to + "\"", StandardCharsets.UTF_8));
        List<String> links = new ArrayList<>();
        for (JsonNode message : found.get("messages")) {
            String text = get("/api/v1/message/" + message.get("ID").asString()).get("Text").asString();
            Matcher link = LINK.matcher(text);
            while (link.find()) {
                if (link.group().contains(path)) {
                    links.add(link.group());
                    break;
                }
            }
        }
        return links;
    }

    private static JsonNode get(String path) {
        try {
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(URL + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Mailpit GET " + path + " -> " + response.statusCode());
            }
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Mailpit GET " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mailpit GET " + path + " interrupted", e);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for an email", e);
        }
    }
}
