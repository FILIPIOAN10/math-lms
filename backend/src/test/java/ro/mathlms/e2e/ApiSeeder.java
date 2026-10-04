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
import java.util.HashMap;
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

    long createClass(String name) {
        return send("POST", "/api/admin/classes", Map.of("name", name)).get("id").asLong();
    }

    void enroll(long classId, String studentEmail) {
        long studentId = userId("STUDENT", studentEmail);
        send("POST", "/api/admin/classes/" + classId + "/enrollments", Map.of("studentId", studentId));
    }

    /** Admin: attaches the student to the parent account (the link the parent dashboard is built on). */
    void linkParent(String studentEmail, String parentEmail) {
        send("POST", "/api/admin/users/" + userId("STUDENT", studentEmail) + "/link-parent",
                Map.of("parentId", userId("PARENT", parentEmail)));
    }

    private long userId(String role, String email) {
        for (JsonNode user : send("GET", "/api/admin/users?role=" + role, null)) {
            if (user.get("email").asText().equals(email)) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("No " + role + " account " + email);
    }

    /**
     * Student side: starts the quiz, picks {@code optionText} on its first single-choice item, and hands the
     * attempt in. The open item stays unanswered, so the attempt ends SUBMITTED (waiting for the teacher).
     */
    long takeAndSubmit(long quizId, String optionText) {
        JsonNode started = send("POST", "/api/quiz/quizzes/" + quizId + "/attempts", Map.of());
        long attemptId = started.get("attemptId").asLong();
        for (JsonNode item : started.get("quiz").get("items")) {
            if (item.get("type").asText().equals("SINGLE_CHOICE")) {
                for (JsonNode option : item.get("options")) {
                    if (option.get("text").asText().equals(optionText)) {
                        send("PUT", "/api/quiz/attempts/" + attemptId + "/responses/" + item.get("id").asLong(),
                                Map.of("optionId", option.get("id").asLong()));
                    }
                }
            }
        }
        send("POST", "/api/quiz/attempts/" + attemptId + "/submit", Map.of());
        return attemptId;
    }

    /** A quiz for every student. */
    long createPublishedQuiz(String title) {
        return createPublishedQuiz(title, null);
    }

    /**
     * One 5-point single-choice item (correct = "Varianta B") + one 10-point open item, then publishes.
     * {@code schoolClassId} null = visible to every student, otherwise only to that class.
     */
    long createPublishedQuiz(String title, Long schoolClassId) {
        Map<String, Object> quiz = new HashMap<>();
        quiz.put("title", title);
        quiz.put("description", "creat de testul E2E");
        quiz.put("schoolClassId", schoolClassId);
        long quizId = send("POST", "/api/admin/quizzes", quiz).get("id").asLong();

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
