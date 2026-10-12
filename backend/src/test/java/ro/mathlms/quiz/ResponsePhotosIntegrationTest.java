package ro.mathlms.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S2 on real Postgres, over HTTP: an open answer takes up to 5 pages, each served by its id to the student and the
 * teacher (never to another student), one can be taken off while the attempt is open, the result lists them in order.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResponsePhotosIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    /** The smallest valid PNG (1x1): the service only checks the declared type, the browser shows it. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;

    private User student;
    private User other;
    private QuizItem open;
    private long attemptId;

    @BeforeEach
    void setUp() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        student = userRepository.save(new User("elev-" + stamp + "@scoala.ro", "Ana", Role.STUDENT));
        other = userRepository.save(new User("altul-" + stamp + "@scoala.ro", "Altul", Role.STUDENT));
        Quiz quiz = new Quiz("Teză " + stamp, null);
        quiz.publish();
        quiz = quizRepository.save(quiz);
        open = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Rezolvă", 10, null));
        String started = mockMvc.perform(post("/api/quiz/quizzes/" + quiz.getId() + "/attempts").with(as(student)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        attemptId = objectMapper.readTree(started).get("attemptId").asLong();
    }

    private RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(STUDENT);
    }

    private ResultActions upload(String name) throws Exception {
        return mockMvc.perform(multipart("/api/quiz/attempts/" + attemptId + "/responses/" + open.getId() + "/photo")
                .file(new MockMultipartFile("file", name, "image/png", PNG)).with(as(student)).with(csrf()));
    }

    private List<Long> ids(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        body.get("photoIds").forEach(id -> ids.add(id.asLong()));
        return ids;
    }

    private String photoPath(long photoId) {
        return "/api/quiz/attempts/" + attemptId + "/responses/" + open.getId() + "/photos/" + photoId;
    }

    @Test
    void upToFivePagesEachServedByIdAndOneCanBeTakenOff() throws Exception {
        List<Long> after = List.of();
        for (int page = 1; page <= ItemResponse.MAX_PHOTOS; page++) {
            after = ids(upload("pagina" + page + ".png").andExpect(status().isOk()));
        }
        assertThat(after).hasSize(5).isSorted();
        upload("pagina6.png").andExpect(status().isBadRequest());

        // the student and the teacher see a page; another student gets the same 404 as for nothing
        mockMvc.perform(get(photoPath(after.get(1))).with(as(student))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId + "/responses/" + open.getId() + "/photos/" + after.get(1))
                .with(user("prof@scoala.ro").roles("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get(photoPath(after.get(1))).with(as(other))).andExpect(status().isNotFound());

        // take page 2 off: the rest stay, in order, and it is gone
        List<Long> left = ids(mockMvc.perform(delete(photoPath(after.get(1))).with(as(student)).with(csrf()))
                .andExpect(status().isOk()));
        assertThat(left).containsExactly(after.get(0), after.get(2), after.get(3), after.get(4));
        mockMvc.perform(get(photoPath(after.get(1))).with(as(student))).andExpect(status().isNotFound());
        mockMvc.perform(delete(photoPath(after.get(0))).with(as(other)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void theHandedInPaperListsThePagesAndTheyCanNoLongerChange() throws Exception {
        List<Long> pages = new ArrayList<>(ids(upload("a.png")));
        pages = ids(upload("b.png"));
        mockMvc.perform(post("/api/quiz/attempts/" + attemptId + "/submit").with(as(student)).with(csrf()))
                .andExpect(status().isOk());

        String result = mockMvc.perform(get("/api/quiz/attempts/" + attemptId + "/result").with(as(student)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(result).get("items").get(0);
        assertThat(item.get("photoUploaded").asBoolean()).isTrue();
        assertThat(item.get("photoIds").size()).isEqualTo(2);
        assertThat(item.get("photoIds").get(0).asLong()).isEqualTo(pages.get(0));

        // once handed in, nothing is added or taken off
        upload("c.png").andExpect(status().is4xxClientError());
        mockMvc.perform(delete(photoPath(pages.get(0))).with(as(student)).with(csrf())).andExpect(status().is4xxClientError());
    }
}
