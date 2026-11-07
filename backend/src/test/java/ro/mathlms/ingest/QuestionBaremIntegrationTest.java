package ro.mathlms.ingest;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The barem of ONE open question read from a photo while the test does not exist yet (the review of "Test din poze"):
 * the teacher frames the problem on the barem photo, sends it with the question as the review shows it, and gets the
 * barem of each requirement back. Nothing is saved in a test; another teacher cannot read it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuestionBaremIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BaremService service;
    @Autowired private BaremReadRepository readRepository;
    @Autowired private AiImportProperties importProperties;

    private User teacher;
    private User otherTeacher;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        teacher = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof", Role.ADMIN));
        otherTeacher = userRepository.save(new User("alt-" + stamp + "@scoala.ro", "Alt prof", Role.ADMIN));
        while (service.processNext()) {
            // other tests' leftovers must not be read as ours
        }
    }

    private RequestPostProcessor as(User u) {
        return user(u.getEmail()).roles("ADMIN");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static final Map<String, Object> PROBLEM = Map.of(
            "statement", "Într-o pungă sunt bomboane.",
            "points", 5,
            "parts", List.of(Map.of("statement", "Verificați dacă pot fi 55 de bomboane.", "points", 2),
                    Map.of("statement", "Care poate fi cel mai mic număr de bomboane?", "points", 3)));

    private ResultActions send(User who, Object question, MockMultipartFile... photos) throws Exception {
        var request = multipart("/api/admin/barem-reads");
        for (MockMultipartFile photo : photos) {
            request.file(photo);
        }
        request.file(new MockMultipartFile("question", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(question)));
        return mockMvc.perform(request.with(as(who)).with(csrf()));
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("files", "barem.png", "image/png", PNG);
    }

    @Test
    void theBaremOfOneQuestionIsReadAndEachRequirementGetsItsOwn() throws Exception {
        long id = json(send(teacher, PROBLEM, photo()).andExpect(status().isAccepted())).get("id").asLong();
        BaremRead stored = readRepository.findById(id).orElseThrow();
        assertThat(stored.getQuizId()).isNull();
        Path photo = Path.of(importProperties.pagesDir(),
                objectMapper.readTree(stored.getPageKeys()).get(0).get("key").asString());
        assertThat(photo).exists();

        assertThat(service.processNext()).isTrue();

        JsonNode done = json(mockMvc.perform(get("/api/admin/barem-reads/" + id).with(as(teacher)))
                .andExpect(status().isOk()));
        assertThat(done.get("status").asString()).isEqualTo("DONE");
        JsonNode barem = done.get("barem");
        JsonNode parts = barem.get("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).get("label").asString()).isEqualTo("a");
        assertThat(parts.get(0).get("barem").asString()).endsWith("1p"); // the fake reader: 1p + (points - 1)p
        assertThat(parts.get(1).get("label").asString()).isEqualTo("b");
        assertThat(parts.get(1).get("barem").asString()).endsWith("2p");
        assertThat(Files.exists(photo)).as("the photo is deleted once read").isFalse();
    }

    @Test
    void aQuestionWithoutRequirementsGetsOneBarem() throws Exception {
        long id = json(send(teacher, Map.of("statement", "Calculați aria.", "points", 5, "parts", List.of()), photo())
                .andExpect(status().isAccepted())).get("id").asLong();
        service.processNext();

        JsonNode barem = json(mockMvc.perform(get("/api/admin/barem-reads/" + id).with(as(teacher)))
                .andExpect(status().isOk())).get("barem");
        assertThat(barem.get("barem").asString()).contains("4p");
        assertThat(barem.get("parts")).isEmpty();
    }

    @Test
    void anotherTeachersReadingIsNotThere() throws Exception {
        long id = json(send(teacher, PROBLEM, photo()).andExpect(status().isAccepted())).get("id").asLong();
        mockMvc.perform(get("/api/admin/barem-reads/" + id).with(as(otherTeacher))).andExpect(status().isNotFound());
    }

    @Test
    void refusesNoPhotoAnotherFileAndAQuestionWithoutText() throws Exception {
        send(teacher, PROBLEM).andExpect(status().isBadRequest());
        send(teacher, PROBLEM, new MockMultipartFile("files", "barem.txt", "text/plain", new byte[]{1}))
                .andExpect(status().isBadRequest());
        send(teacher, Map.of("statement", " ", "points", 5, "parts", List.of()), photo())
                .andExpect(status().isBadRequest());
    }
}
