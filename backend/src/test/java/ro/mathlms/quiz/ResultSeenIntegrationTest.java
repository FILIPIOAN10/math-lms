package ro.mathlms.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S3 on real Postgres: a graded test is "Nou" until its student opens the result; a new teacher comment re-news it. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResultSeenIntegrationTest {

    // What JwtCookieAuthFilter grants an active student: the role, and the active status /api/quiz/** requires.
    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository attemptRepository;

    private User student;
    private QuizAttempt attempt;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        student = userRepository.save(new User("elev-" + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        Quiz quiz = quizRepository.save(new Quiz("Teză " + stamp, null));
        QuizAttempt started = new QuizAttempt(quiz, student);
        started.submit();
        started.markGraded(6);
        attempt = attemptRepository.save(started);
    }

    private RequestPostProcessor asStudent() {
        return user(student.getEmail()).authorities(STUDENT);
    }

    private boolean isNew() throws Exception {
        String body = mockMvc.perform(get("/api/quiz/attempts").with(asStudent()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode row : objectMapper.readTree(body)) {
            if (row.get("attemptId").asLong() == attempt.getId()) {
                return row.get("newResult").asBoolean();
            }
        }
        throw new AssertionError("attempt not listed");
    }

    private void open() throws Exception {
        mockMvc.perform(put("/api/quiz/attempts/" + attempt.getId() + "/result-seen").with(asStudent()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void aGradedTestIsNewUntilOpenedAndANewTeacherCommentMakesItNewAgain() throws Exception {
        assertThat(isNew()).isTrue();

        open();
        open(); // idempotent
        assertThat(isNew()).isFalse();

        mockMvc.perform(put("/api/admin/quiz/attempts/" + attempt.getId() + "/comment")
                        .with(user("prof@scoala.ro").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Vezi subiectul 3.\"}"))
                .andExpect(status().isNoContent());
        assertThat(isNew()).isTrue();
    }

    @Test
    void anotherStudentCannotMarkItSeenAndGetsTheSame404AsForNothing() throws Exception {
        mockMvc.perform(put("/api/quiz/attempts/" + attempt.getId() + "/result-seen")
                        .with(user("altcineva@scoala.ro").authorities(STUDENT)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/quiz/attempts/999999999/result-seen").with(asStudent()).with(csrf()))
                .andExpect(status().isNotFound());
        // ...and so is reading their result (it was a 403, which confirmed the attempt exists)
        mockMvc.perform(get("/api/quiz/attempts/" + attempt.getId() + "/result")
                        .with(user("altcineva@scoala.ro").authorities(STUDENT)))
                .andExpect(status().isNotFound());

        assertThat(isNew()).isTrue();
    }
}
