package ro.mathlms.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Seeds test data over the REST API (faster and far less brittle than driving the quiz-builder dialogs).
 * Mirrors what the SPA does: login sets the auth cookies; writes echo the XSRF-TOKEN cookie as the
 * X-XSRF-TOKEN header (cookie double-submit CSRF).
 */
final class ApiSeeder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiUrl;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    ApiSeeder(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    void login(String email, String password) {
        send("POST", "/api/auth/login", Map.of("email", email, "password", password));
        send("GET", "/api/auth/me", null); // any response after login carries the XSRF-TOKEN cookie
    }

    /** One 5-point single-choice item (correct = "Varianta B") + one 10-point open item, then publishes. */
    long createPublishedQuiz(String title) {
        long quizId = send("POST", "/api/admin/quizzes",
                Map.of("title", title, "description", "creat de testul E2E")).get("id").asLong();

        send("POST", "/api/admin/quizzes/" + quizId + "/items", Map.of(
                "type", "SINGLE_CHOICE",
                "position", 1,
                "statement", "Alege varianta B.",
                "points", 5,
                "options", List.of(
                        Map.of("position", 1, "text", "Varianta A", "correct", false),
                        Map.of("position", 2, "text", "Varianta B", "correct", true),
                        Map.of("position", 3, "text", "Varianta C", "correct", false))));

        send("POST", "/api/admin/quizzes/" + quizId + "/items", Map.of(
                "type", "OPEN",
                "position", 2,
                "statement", "Rezolvă pe hârtie și fotografiază rezolvarea.",
                "points", 10,
                "solution", "Barem: x1 = 2, x2 = 3"));

        send("POST", "/api/admin/quizzes/" + quizId + "/publish", Map.of());
        return quizId;
    }

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(apiUrl + path))
                    .header("Content-Type", "application/json");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            if (!method.equals("GET")) {
                xsrfToken().ifPresent(token -> request.header("X-XSRF-TOKEN", token));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(method + " " + path + " -> " + response.statusCode()
                        + " " + response.body());
            }
            return response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + path + " interrupted", e);
        }
    }

    private java.util.Optional<String> xsrfToken() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst();
    }
}
