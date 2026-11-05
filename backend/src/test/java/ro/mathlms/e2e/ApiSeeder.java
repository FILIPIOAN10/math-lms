package ro.mathlms.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    /** Admin: a book of a class. Returns its id. */
    long createBook(long classId, String title) {
        return send("POST", "/api/admin/classes/" + classId + "/books", Map.of("title", title)).get("id").asLong();
    }

    /** Admin: a chapter of a book. Returns its id. */
    long createChapter(long bookId, String title) {
        return send("POST", "/api/admin/books/" + bookId + "/chapters", Map.of("title", title)).get("id").asLong();
    }

    /** Admin: an exercise of a chapter (no solution, no difficulty). Returns its id. */
    long createExercise(long chapterId, String statement) {
        return send("POST", "/api/admin/chapters/" + chapterId + "/exercises", Map.of("statement", statement)).get("id").asLong();
    }

    // --- accounts made the way newcomers make them: invite link, sign-up, the link from the email ---

    /** Admin: a class's invite link for at most {@code maxUses} students. Returns the link's URL. */
    String classInvite(long classId, int maxUses) {
        return send("POST", "/api/admin/classes/" + classId + "/invites", Map.of("maxUses", maxUses)).get("url").asString();
    }

    /** Admin: a single-use link for a new teacher (approved by hand after sign-up). */
    String teacherInvite() {
        return send("POST", "/api/admin/invites/admin", Map.of()).get("url").asString();
    }

    /** Admin: the general link for {@code role} (STUDENT or PARENT); every account made with it waits for approval. */
    String generalInvite(String role) {
        return send("POST", "/api/admin/invites", Map.of("role", role)).get("url").asString();
    }

    /** Admin or teacher: the single-use link for the parent of this student. */
    String parentInvite(String studentEmail) {
        return send("POST", "/api/admin/users/" + userId("STUDENT", studentEmail) + "/parent-invite", Map.of())
                .get("url").asString();
    }

    /** Signed out: the request the sign-up form sends for an invite link. */
    void signUp(String inviteUrl, String fullName, String email, String password) {
        primeXsrf();
        send("POST", "/api/auth/register", Map.of("email", email, "fullName", fullName, "password", password,
                "inviteToken", queryParam(inviteUrl, "token")));
    }

    /** The request the page behind the confirmation email's link sends. */
    void confirmEmail(String verifyLink) {
        send("GET", "/api/auth/verify-email?token=" + queryParam(verifyLink, "token"), null);
    }

    /** Admin: approves the waiting account with this email as {@code role}. */
    void approve(String email, String role) {
        for (JsonNode pending : send("GET", "/api/admin/users/pending", null)) {
            if (pending.get("email").asString().equals(email)) {
                send("POST", "/api/admin/users/" + pending.get("id").asLong() + "/approve", Map.of("role", role));
                return;
            }
        }
        throw new IllegalStateException("No pending account " + email);
    }

    /** Admin: whether an active account with this role and email exists. */
    boolean isActive(String role, String email) {
        for (JsonNode user : send("GET", "/api/admin/users?role=" + role, null)) {
            if (user.get("email").asString().equals(email)) {
                return true;
            }
        }
        return false;
    }

    /** Admin: the id of the student's parent, or null. */
    Long parentIdOf(String studentEmail) {
        for (JsonNode user : send("GET", "/api/admin/users?role=STUDENT", null)) {
            if (user.get("email").asString().equals(studentEmail)) {
                JsonNode parentId = user.get("parentId");
                return parentId == null || parentId.isNull() ? null : parentId.asLong();
            }
        }
        throw new IllegalStateException("No STUDENT account " + studentEmail);
    }

    /** Admin: the student ids enrolled in a class. */
    List<Long> enrolledStudentIds(long classId) {
        List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode enrollment : send("GET", "/api/admin/classes/" + classId + "/enrollments", null)) {
            ids.add(enrollment.get("studentId").asLong());
        }
        return ids;
    }

    /** A signed-out client has no XSRF-TOKEN cookie until its first request: ask who it is (401) to get one. */
    private void primeXsrf() {
        if (xsrfToken().isPresent()) {
            return;
        }
        try {
            send("GET", "/api/auth/me", null);
        } catch (IllegalStateException signedOut) {
            // expected: 401, and the response set the cookie
        }
    }

    private static String queryParam(String url, String name) {
        String query = URI.create(url).getRawQuery();
        for (String pair : query == null ? new String[0] : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("No " + name + " in " + url);
    }

    private long userId(String role, String email) {
        for (JsonNode user : send("GET", "/api/admin/users?role=" + role, null)) {
            if (user.get("email").asString().equals(email)) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("No " + role + " account " + email);
    }

    /**
     * Student side: starts the quiz, picks the options with these texts on its single-choice items, and hands the
     * attempt in. Open items stay unanswered, so a quiz with one ends SUBMITTED (waiting for the teacher); an all-grilă
     * quiz is graded on the spot.
     */
    long takeAndSubmit(long quizId, String... optionTexts) {
        java.util.Set<String> wanted = java.util.Set.of(optionTexts);
        JsonNode started = send("POST", "/api/quiz/quizzes/" + quizId + "/attempts", Map.of());
        long attemptId = started.get("attemptId").asLong();
        for (JsonNode item : started.get("quiz").get("items")) {
            if (item.get("type").asString().equals("SINGLE_CHOICE")) {
                for (JsonNode option : item.get("options")) {
                    if (wanted.contains(option.get("text").asString())) {
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

    /**
     * Admin: makes sure {@code email} is an active student. The CI seed (deploy/e2e-seed.sql) leaves student.nou
     * waiting for approval, a dev database usually has it approved already - approving here keeps the test the same.
     */
    void ensureActiveStudent(String email) {
        for (JsonNode user : send("GET", "/api/admin/users?role=STUDENT", null)) {
            if (user.get("email").asString().equals(email)) {
                return;
            }
        }
        for (JsonNode pending : send("GET", "/api/admin/users/pending", null)) {
            if (pending.get("email").asString().equals(email)) {
                send("POST", "/api/admin/users/" + pending.get("id").asLong() + "/approve", Map.of("role", "STUDENT"));
                return;
            }
        }
        throw new IllegalStateException("No student or pending account " + email);
    }

    // --- building blocks for the newer flows (T1-T4, EN sections, pictures, S3/S4, F1) ---

    /** A DRAFT quiz; {@code schoolClassId} null = for every student. */
    long createDraftQuiz(String title, Long schoolClassId, boolean practiceAllowed) {
        Map<String, Object> quiz = new HashMap<>();
        quiz.put("title", title);
        quiz.put("description", "creat de testul E2E");
        quiz.put("schoolClassId", schoolClassId);
        quiz.put("practiceAllowed", practiceAllowed);
        return send("POST", "/api/admin/quizzes", quiz).get("id").asLong();
    }

    /** A grilă with two options; {@code section} (e.g. "SUBIECTUL I") may be null. Returns the item id. */
    long addChoice(long quizId, int position, String statement, String right, String wrong, String section) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", "SINGLE_CHOICE");
        item.put("position", position);
        item.put("statement", statement);
        item.put("points", 5);
        item.put("solution", "Barem: " + right);
        item.put("section", section);
        item.put("options", List.of(
                Map.of("position", 0, "text", right, "correct", true),
                Map.of("position", 1, "text", wrong, "correct", false)));
        return send("POST", "/api/admin/quizzes/" + quizId + "/items", item).get("id").asLong();
    }

    /** A grilă still waiting for its right answer, the way "Importă din text" or the AI import leaves one. */
    long addChoiceWithoutAnswer(long quizId, int position, String statement) {
        return send("POST", "/api/admin/quizzes/" + quizId + "/items", Map.of(
                "type", "SINGLE_CHOICE", "position", position, "statement", statement, "points", 5,
                "options", List.of(
                        Map.of("position", 0, "text", "unu", "correct", false),
                        Map.of("position", 1, "text", "doi", "correct", false)))).get("id").asLong();
    }

    /** Student side: starts an attempt and leaves it open (a student writing right now). Returns the attempt id. */
    long startAttempt(long quizId) {
        return send("POST", "/api/quiz/quizzes/" + quizId + "/attempts", Map.of()).get("attemptId").asLong();
    }

    // --- chapter 5 (the student takes the test): any test, any question, homework, grading, EN lessons ---

    /** A DRAFT test with every setting the editor has: {@code timeLimitMinutes} and {@code maxAttempts} may be null. */
    long createQuiz(String title, Long schoolClassId, Integer timeLimitMinutes, boolean practiceAllowed, Integer maxAttempts) {
        Map<String, Object> quiz = new HashMap<>();
        quiz.put("title", title);
        quiz.put("schoolClassId", schoolClassId);
        quiz.put("timeLimitMinutes", timeLimitMinutes);
        quiz.put("practiceAllowed", practiceAllowed);
        quiz.put("maxAttempts", maxAttempts);
        return send("POST", "/api/admin/quizzes", quiz).get("id").asLong();
    }

    /** Any question, as the editor sends it (type, statement, points, options, hints, numericAnswer...). Returns its id. */
    long addItem(long quizId, Map<String, Object> item) {
        return send("POST", "/api/admin/quizzes/" + quizId + "/items", item).get("id").asLong();
    }

    /** Admin: gives the test as homework to a class, due at {@code dueAt} (only in the future). Returns the homework's id. */
    long assign(long quizId, long classId, java.time.Instant dueAt) {
        return send("POST", "/api/admin/assignments",
                Map.of("quizId", quizId, "schoolClassId", classId, "dueAt", dueAt.toString())).get("id").asLong();
    }

    /** Admin: moves a homework's deadline (the server takes only a future one; a refusal throws with its message). */
    void reschedule(long assignmentId, java.time.Instant dueAt) {
        send("PUT", "/api/admin/assignments/" + assignmentId, Map.of("dueAt", dueAt.toString()));
    }

    /** Admin: gives a class to a teacher, who then sees it, its students and its homework. */
    void giveClassTo(long classId, String teacherEmail) {
        send("PUT", "/api/admin/classes/" + classId + "/teacher", Map.of("teacherId", userId("TEACHER", teacherEmail)));
    }

    /** Teacher: points for one open answer, then the grade is final. */
    void gradeAndFinalize(long attemptId, long openItemId, int points) {
        send("PUT", "/api/admin/quiz/attempts/" + attemptId + "/responses/" + openItemId + "/grade", Map.of("points", points));
        send("POST", "/api/admin/quiz/attempts/" + attemptId + "/mark-graded", Map.of());
    }

    /** Student side: their attempts at this quiz (status, score, maxScore...), oldest first as the server lists them. */
    List<JsonNode> myAttempts(long quizId) {
        List<JsonNode> mine = new java.util.ArrayList<>();
        for (JsonNode attempt : send("GET", "/api/quiz/attempts", null)) {
            if (attempt.get("quizId").asLong() == quizId) {
                mine.add(attempt);
            }
        }
        return mine;
    }

    /** Admin: an EN chapter whose page has one worked example, so a wrong answer links to it ("ready"). Returns its id. */
    long createEnLesson(String code, String title) {
        Map<String, Object> chapter = new HashMap<>();
        chapter.put("code", code);
        chapter.put("title", title);
        chapter.put("area", "ALGEBRA");
        long id = send("POST", "/api/admin/en-chapters", chapter).get("id").asLong();
        send("POST", "/api/admin/en-chapters/" + id + "/examples",
                Map.of("statement", "Calculează $\\frac{1}{2}+\\frac{1}{4}$.", "solution", "Numitorul comun e 4: $\\frac{3}{4}$."));
        return id;
    }

    /** Admin: removes an EN chapter made by {@link #createEnLesson}: its examples first (a page with content stays). */
    void deleteEnLesson(long id) {
        for (JsonNode example : send("GET", "/api/en-chapters/" + id, null).get("examples")) {
            send("DELETE", "/api/admin/en-chapters/" + id + "/examples/" + example.get("id").asLong(), null);
        }
        send("DELETE", "/api/admin/en-chapters/" + id, null);
    }

    /**
     * Student side: sends a file as the answer to an open question, the way the photo button does. Returns the HTTP
     * status and body instead of throwing, so a refusal can be checked.
     */
    HttpResponse<String> uploadAnswerPhoto(long attemptId, long itemId, java.nio.file.Path file, String contentType) {
        return multipart("POST", "/api/quiz/attempts/" + attemptId + "/responses/" + itemId + "/photo", file, contentType);
    }

    /** Admin: whether students already have attempts at this test (its questions are frozen then). */
    boolean isTaken(long quizId) {
        return send("GET", "/api/admin/quizzes/" + quizId, null).get("taken").asBoolean();
    }

    /** An open question worth {@code points}. Returns the item id. */
    long addOpen(long quizId, int position, String statement, int points) {
        return send("POST", "/api/admin/quizzes/" + quizId + "/items", Map.of(
                "type", "OPEN", "position", position, "statement", statement, "points", points,
                "solution", "Barem: rezolvare completă")).get("id").asLong();
    }

    void publish(long quizId) {
        send("POST", "/api/admin/quizzes/" + quizId + "/publish", Map.of());
    }

    /** The picture under a question's statement (multipart, like the editor's "Alege imaginea"). */
    void attachImage(long itemId, java.nio.file.Path png) {
        HttpResponse<String> response = multipart("PUT", "/api/admin/quiz-items/" + itemId + "/image", png, "image/png");
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("PUT image -> " + response.statusCode() + " " + response.body());
        }
    }

    /** One file as the multipart field "file", the way the app's upload buttons send it. */
    private HttpResponse<String> multipart(String method, String path, java.nio.file.Path file, String contentType) {
        try {
            String boundary = "----e2e" + System.nanoTime();
            byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + file.getFileName() + "\"\r\nContent-Type: " + contentType + "\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            byte[] body = new byte[head.length + bytes.length + tail.length];
            System.arraycopy(head, 0, body, 0, head.length);
            System.arraycopy(bytes, 0, body, head.length, bytes.length);
            System.arraycopy(tail, 0, body, head.length + bytes.length, tail.length);
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(apiUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            xsrfToken().ifPresent(token -> request.header("X-XSRF-TOKEN", token));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + path + " interrupted", e);
        }
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
    /** Student side: how many attempts the logged-in student has at this quiz (G4: a double submit must make one). */
    long countMyAttempts(long quizId) {
        long count = 0;
        for (JsonNode attempt : send("GET", "/api/quiz/attempts", null)) {
            if (attempt.get("quizId").asLong() == quizId) {
                count++;
            }
        }
        return count;
    }

    /** Returnează ID-ul unui utilizator pentru teste (ex: IDOR) */
    long getUserIdForTest(String role, String email) {
        return userId(role, email);
    }
}
