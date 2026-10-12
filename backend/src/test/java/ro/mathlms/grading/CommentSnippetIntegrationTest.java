package ro.mathlms.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saved grading comments on real Postgres, over HTTP: own list, most used first, no duplicates, nobody else's. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CommentSnippetIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CommentSnippetRepository repository;

    private User teacher;
    private User other;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        teacher = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof", Role.ADMIN));
        other = userRepository.save(new User("alt-" + stamp + "@scoala.ro", "Alt", Role.ADMIN));
    }

    private RequestPostProcessor as(User u) {
        return user(u.getEmail()).roles("ADMIN");
    }

    private ResultActions create(User who, String text) throws Exception {
        return mockMvc.perform(post("/api/admin/comment-snippets").with(as(who)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(java.util.Map.of("text", text))));
    }

    private long id(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private List<String> texts(User who) throws Exception {
        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/admin/comment-snippets").with(as(who)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> texts = new ArrayList<>();
        list.forEach(s -> texts.add(s.get("text").asText()));
        return texts;
    }

    @Test
    void savedCommentsAreListedMostUsedFirst() throws Exception {
        long sign = id(create(teacher, "  Atenție la semn.  ").andExpect(status().isCreated()));
        long unit = id(create(teacher, "Lipsește unitatea de măsură.").andExpect(status().isCreated()));
        create(teacher, "Raționament corect.").andExpect(status().isCreated());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/admin/comment-snippets/" + unit + "/use").with(as(teacher)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/admin/comment-snippets/" + sign + "/use").with(as(teacher)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(texts(teacher)).containsExactly("Lipsește unitatea de măsură.", "Atenție la semn.", "Raționament corect.");
    }

    @Test
    void theSameCommentInAnotherCaseIsNotSavedTwice() throws Exception {
        long first = id(create(teacher, "Atenție la semn.").andExpect(status().isCreated()));
        long again = id(create(teacher, "ATENȚIE LA SEMN.").andExpect(status().isCreated()));

        assertThat(again).isEqualTo(first);
        assertThat(texts(teacher)).containsExactly("Atenție la semn.");
        // the unique index is the last guard, whatever the service checks first
        assertThatThrownBy(() -> repository.saveAndFlush(new CommentSnippet(teacher.getId(), "atenție la semn.", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        create(teacher, "   ").andExpect(status().isBadRequest());
    }

    @Test
    void anotherTeachersCommentsAreNotThere() throws Exception {
        long mine = id(create(teacher, "Bine!"));

        assertThat(texts(other)).isEmpty();
        mockMvc.perform(post("/api/admin/comment-snippets/" + mine + "/use").with(as(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/comment-snippets/" + mine).with(as(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/comment-snippets/" + mine).with(as(teacher)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(texts(teacher)).isEmpty();
        mockMvc.perform(get("/api/admin/comment-snippets").with(user("elev@scoala.ro").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTeacherCannotHoardMoreThanTheLimit() throws Exception {
        for (int i = 0; i < CommentSnippetService.MAX_PER_TEACHER; i++) {
            repository.save(new CommentSnippet(teacher.getId(), "Comentariul " + i, Instant.now()));
        }
        create(teacher, "Unul în plus").andExpect(status().isConflict());
        create(teacher, "comentariul 7").andExpect(status().isCreated()); // an existing one is still "saved"
    }
}
