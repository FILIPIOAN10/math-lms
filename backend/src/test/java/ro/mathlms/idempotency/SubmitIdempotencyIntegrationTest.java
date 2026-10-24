package ro.mathlms.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3 on real Postgres: a hand-in retried with the same Idempotency-Key (the first answer was lost on the way back)
 * gets the first result again instead of "already handed in"; the same key from another account is refused, never
 * replayed; without a key the old behaviour stays.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SubmitIdempotencyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository attemptRepository;

    private User student;
    private QuizAttempt attempt;

    @BeforeEach
    void setUp() {
        student = active("elev");
        Quiz quiz = quizRepository.save(new Quiz("Test predare " + System.nanoTime(), null));
        attempt = attemptRepository.save(new QuizAttempt(quiz, student));
    }

    private User active(String who) {
        User user = User.registerLocal(who + "." + System.nanoTime() + "@scoala.ro", "Nume " + who, "HASH", Role.STUDENT);
        user.verifyEmail();
        user.approve(Role.STUDENT);
        return userRepository.save(user);
    }

    private static RequestPostProcessor as(User account) {
        return user(account.getEmail()).authorities(
                new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    private String submitUrl() {
        return "/api/quiz/attempts/" + attempt.getId() + "/submit";
    }

    @Test
    void aRetryWithTheSameKeyGetsTheFirstResultAgain() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post(submitUrl()).with(as(student)).with(csrf()).header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attempt.getId().intValue()));
        mockMvc.perform(post(submitUrl()).with(as(student)).with(csrf()).header("Idempotency-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attempt.getId().intValue()));

        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getStatus())
                .isNotEqualTo(QuizAttemptStatus.IN_PROGRESS);
    }

    @Test
    void withoutAKeyASecondHandInIsStillRefused() throws Exception {
        mockMvc.perform(post(submitUrl()).with(as(student)).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post(submitUrl()).with(as(student)).with(csrf())).andExpect(status().is4xxClientError());
    }

    @Test
    void anotherAccountCannotReplayTheResultWithTheSameKey() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post(submitUrl()).with(as(student)).with(csrf()).header("Idempotency-Key", key))
                .andExpect(status().isOk());

        mockMvc.perform(post(submitUrl()).with(as(active("intrus"))).with(csrf()).header("Idempotency-Key", key))
                .andExpect(status().isUnprocessableContent());
    }
}
