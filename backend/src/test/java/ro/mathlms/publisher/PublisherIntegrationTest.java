package ro.mathlms.publisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Edituri": publishers exist only once an admin adds them (none ships with the app), a teacher may pick them but not
 * manage them, and a test may name one - or none, for an official Evaluarea Națională paper.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublisherIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private User admin;
    private String stamp;

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        admin = userRepository.save(new User("admin-" + stamp + "@scoala.ro", "Admin", Role.ADMIN));
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getEmail()).roles("ADMIN");
    }

    private static RequestPostProcessor asTeacher() {
        return user("prof@scoala.ro").roles("TEACHER");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               Object body, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private long create(String name, String description) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", description);
        return json(send(post("/api/admin/publishers"), body, asAdmin()).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode listed(long id) throws Exception {
        for (JsonNode p : json(mockMvc.perform(get("/api/admin/publishers").with(asAdmin())).andExpect(status().isOk()))) {
            if (p.get("id").asLong() == id) {
                return p;
            }
        }
        return null;
    }

    /** The test's row in the Teste list (GET /api/admin/quizzes), or null. */
    private JsonNode quizRow(long quizId) throws Exception {
        for (JsonNode q : json(mockMvc.perform(get("/api/admin/quizzes").with(asAdmin())).andExpect(status().isOk()))) {
            if (q.get("id").asLong() == quizId) {
                return q;
            }
        }
        return null;
    }

    @Test
    void noPublisherShipsWithTheApp() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V53__publishers.sql"));
        assertThat(migration.toUpperCase()).doesNotContain("INSERT");
    }

    @Test
    void anAdminAddsEditsAndListsPublishers() throws Exception {
        long id = create("  Art Klett " + stamp + "  ", "Manuale și culegeri");

        JsonNode p = listed(id);
        assertThat(p.get("name").asString()).isEqualTo("Art Klett " + stamp); // trimmed
        assertThat(p.get("description").asString()).isEqualTo("Manuale și culegeri");
        assertThat(p.get("hasLogo").asBoolean()).isFalse();
        assertThat(p.get("quizCount").asLong()).isZero();

        send(put("/api/admin/publishers/" + id), Map.of("name", "Intuitext " + stamp), asAdmin()).andExpect(status().isOk());
        JsonNode edited = listed(id);
        assertThat(edited.get("name").asString()).isEqualTo("Intuitext " + stamp);
        assertThat(edited.get("description").isNull()).isTrue();
    }

    @Test
    void aNameIsRequiredAndUniqueWhateverItsCase() throws Exception {
        create("Litera " + stamp, null);
        String message = send(post("/api/admin/publishers"), Map.of("name", "LITERA " + stamp), asAdmin())
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(message).contains("Există deja");
        send(post("/api/admin/publishers"), Map.of("name", "   "), asAdmin()).andExpect(status().isBadRequest());
    }

    @Test
    void aTeacherPicksFromTheListButDoesNotManageIt() throws Exception {
        long id = create("CD Press " + stamp, null);
        mockMvc.perform(get("/api/admin/publishers").with(asTeacher())).andExpect(status().isOk());
        send(post("/api/admin/publishers"), Map.of("name", "Altă editură " + stamp), asTeacher())
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/publishers/" + id).with(asTeacher()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void aLogoIsAddedShownAndRemoved() throws Exception {
        long id = create("Editura cu siglă " + stamp, null);

        mockMvc.perform(multipart("/api/admin/publishers/" + id + "/logo")
                        .file(new MockMultipartFile("file", "sigla.png", "image/png", PNG))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(listed(id).get("hasLogo").asBoolean()).isTrue();
        mockMvc.perform(get("/api/admin/publishers/" + id + "/logo").with(asTeacher()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(multipart("/api/admin/publishers/" + id + "/logo")
                        .file(new MockMultipartFile("file", "sigla.txt", "text/plain", new byte[]{1}))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/publishers/" + id + "/logo").with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(listed(id).get("hasLogo").asBoolean()).isFalse();
        mockMvc.perform(get("/api/admin/publishers/" + id + "/logo").with(asAdmin())).andExpect(status().isNotFound());
    }

    @Test
    void aTestNamesAPublisherOrNoneAndKeepsWorkingWhenItIsDeleted() throws Exception {
        long publisher = create("Art Klett " + stamp, null);

        Map<String, Object> quiz = new HashMap<>();
        quiz.put("title", "Varianta 1 " + stamp);
        quiz.put("publisherId", publisher);
        JsonNode created = json(send(post("/api/admin/quizzes"), quiz, asAdmin()).andExpect(status().isCreated()));
        long quizId = created.get("id").asLong();
        assertThat(created.get("publisherId").asLong()).isEqualTo(publisher);
        assertThat(created.get("publisherName").asString()).isEqualTo("Art Klett " + stamp);
        assertThat(listed(publisher).get("quizCount").asLong()).isEqualTo(1);
        // Read back outside the request's transaction (open-in-view is off): the list, the test page and a copy name it.
        assertThat(quizRow(quizId).get("publisherName").asString()).isEqualTo("Art Klett " + stamp);
        assertThat(json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(asAdmin())).andExpect(status().isOk()))
                .get("publisherName").asString()).isEqualTo("Art Klett " + stamp);
        JsonNode copy = json(mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/copy").with(asAdmin()).with(csrf()))
                .andExpect(status().isCreated()));
        assertThat(copy.get("publisherName").asString()).isEqualTo("Art Klett " + stamp);
        mockMvc.perform(delete("/api/admin/quizzes/" + copy.get("id").asLong()).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());

        // An official EN paper: no publisher.
        quiz.put("publisherId", null);
        JsonNode official = json(send(put("/api/admin/quizzes/" + quizId), quiz, asAdmin()).andExpect(status().isOk()));
        assertThat(official.get("publisherId").isNull()).isTrue();

        quiz.put("publisherId", publisher);
        send(put("/api/admin/quizzes/" + quizId), quiz, asAdmin()).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/publishers/" + publisher).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());

        JsonNode same = quizRow(quizId);
        assertThat(same).as("the test stays").isNotNull();
        assertThat(same.get("publisherId").isNull()).isTrue();
    }

    @Test
    void anUnknownPublisherIsRefused() throws Exception {
        send(post("/api/admin/quizzes"), Map.of("title", "T " + stamp, "publisherId", 999_999_999), asAdmin())
                .andExpect(status().isNotFound());
        send(put("/api/admin/publishers/999999999"), Map.of("name", "X " + stamp), asAdmin())
                .andExpect(status().isNotFound());
    }
}
