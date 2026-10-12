package ro.mathlms.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 2 on real Postgres, over HTTP: the browser retries unsent answers, so an old one can arrive late. The server
 * keeps the newest per browser, still answers the late one with success (so the browser stops retrying), and lets
 * another device or an older client always through.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AnswerOrderingIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private ItemResponseRepository responseRepository;

    private User student;
    private QuizItem grila;
    private QuizOption b;
    private QuizOption c;
    private long attemptId;

    @BeforeEach
    void setUp() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        student = userRepository.save(new User("elev-" + stamp + "@scoala.ro", "Ana", Role.STUDENT));
        Quiz quiz = new Quiz("Ordine " + stamp, null);
        quiz.publish();
        quiz = quizRepository.save(quiz);
        grila = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "Alege", 5, null));
        b = optionRepository.save(new QuizOption(grila, 0, "B", false));
        c = optionRepository.save(new QuizOption(grila, 1, "C", true));
        String started = mockMvc.perform(post("/api/quiz/quizzes/" + quiz.getId() + "/attempts")
                        .with(user(student.getEmail()).authorities(STUDENT)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        attemptId = objectMapper.readTree(started).get("attemptId").asLong();
    }

    private ResultActions answer(QuizOption option, UUID clientId, Long seq) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("optionId", option.getId());
        body.put("clientId", clientId);
        body.put("clientSeq", seq);
        return mockMvc.perform(put("/api/quiz/attempts/" + attemptId + "/responses/" + grila.getId())
                .with(user(student.getEmail()).authorities(STUDENT)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private Long stored() {
        return responseRepository.findByAttemptIdAndItemId(attemptId, grila.getId()).orElseThrow()
                .getSelectedOption().getId();
    }

    @Test
    void aLateRetryIsAcknowledgedButDoesNotOverwriteTheNewerAnswer() throws Exception {
        UUID browser = UUID.randomUUID();
        answer(c, browser, 2L).andExpect(status().isNoContent());
        answer(b, browser, 1L).andExpect(status().isNoContent()); // sent first, arrived second

        assertThat(stored()).isEqualTo(c.getId());
    }

    @Test
    void anotherDeviceAndAnOlderClientAreAlwaysApplied() throws Exception {
        answer(c, UUID.randomUUID(), 9L).andExpect(status().isNoContent());
        answer(b, UUID.randomUUID(), 1L).andExpect(status().isNoContent());
        assertThat(stored()).isEqualTo(b.getId());

        answer(c, null, null).andExpect(status().isNoContent());
        assertThat(stored()).isEqualTo(c.getId());
    }

    @Test
    void aNegativeCounterIsRefused() throws Exception {
        answer(c, UUID.randomUUID(), -1L).andExpect(status().isBadRequest());
    }
}
