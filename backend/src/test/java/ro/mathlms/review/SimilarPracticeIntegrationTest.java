package ro.mathlms.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.enchapter.EnArea;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterExample;
import ro.mathlms.enchapter.EnChapterExampleRepository;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „Dă-mi una asemănătoare" on real Postgres + pgvector: after a mistake the student gets the nearest grile and worked
 * examples by meaning (vectors written here by hand, so the distances are known) - only grile whose answers they may
 * already see, never the question itself nor a copy of it - and answers them, checked at once, nothing stored.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class SimilarPracticeIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private EnChapterRepository chapterRepository;
    @Autowired private EnChapterExampleRepository exampleRepository;
    @Autowired private JdbcTemplate jdbc;

    private User ana;
    private EnChapter chapter;
    // Every test draws its own directions, so the rows other tests left in the shared database are far from these.
    private int axis;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("ana." + System.nanoTime() + "@scoala.ro", "Ana", Role.STUDENT));
        String base36 = "0000000" + Long.toString(System.nanoTime() / 100, 36).toUpperCase();
        chapter = chapterRepository.save(new EnChapter("A" + base36.substring(base36.length() - 7), "Ordinea operațiilor",
                EnArea.ALGEBRA, 970, null));
        axis = (int) (Math.abs(System.nanoTime() / 100) % 300) * 5; // five free axes per test
    }

    /** A direction: 1 on this test's main axis, {@code tilt} on its axis number {@code k} (1..4). */
    private String vector(int k, double tilt) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (int i = 0; i < 1536; i++) {
            joiner.add(i == axis ? "1" : (k > 0 && i == axis + k) ? String.valueOf(tilt) : "0");
        }
        return joiner.toString();
    }

    /** Cosine distance from the main axis: tilt 0.3 → 0.042, 0.4 → 0.072, 0.5 → 0.106; k = -1 → far away (1.0). */
    private String far() {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (int i = 0; i < 1536; i++) {
            joiner.add(i == axis + 4 ? "1" : "0");
        }
        return joiner.toString();
    }

    private void index(QuizItem item, String vector) {
        jdbc.update("INSERT INTO similar_index (item_id, content_hash, embedded_with, embedding) VALUES (?, ?, 'm', ?::vector)",
                item.getId(), "h".repeat(64), vector);
    }

    private void index(EnChapterExample example, String vector) {
        jdbc.update("INSERT INTO similar_index (example_id, content_hash, embedded_with, embedding) VALUES (?, ?, 'm', ?::vector)",
                example.getId(), "h".repeat(64), vector);
    }

    private Quiz test(boolean practiceAllowed) {
        Quiz q = new Quiz("Test " + System.nanoTime(), null);
        q.allowPractice(practiceAllowed);
        q.limitAttempts(1);
        q.publish();
        return quizRepository.save(q);
    }

    private QuizItem grila(Quiz quiz, int position, String statement) {
        QuizItem item = itemRepository.save(new QuizItem(quiz, position, QuizItemType.SINGLE_CHOICE, statement, 5, null));
        optionRepository.save(new QuizOption(item, 0, "14", true));
        optionRepository.save(new QuizOption(item, 1, "20", false));
        return item;
    }

    private Long option(QuizItem item, boolean correct) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == correct).findFirst().orElseThrow().getId();
    }

    /** Ana writes the test and hands it in, answering each of its questions wrong. */
    private void handIn(Quiz quiz, QuizItem... items) {
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail(), AttemptMode.TEST);
        for (QuizItem item : items) {
            attemptService.saveResponse(attempt.attemptId(), item.getId(), option(item, false), ana.getEmail());
        }
        attemptService.submit(attempt.attemptId(), ana.getEmail());
    }

    private RequestPostProcessor asAna() {
        return user(ana.getEmail()).authorities(STUDENT);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode similar(QuizItem source) throws Exception {
        return json(mockMvc.perform(get("/api/quiz/items/" + source.getId() + "/similar").with(asAna()))
                .andExpect(status().isOk()));
    }

    private ResultActions answer(QuizItem item, Long optionId) throws Exception {
        return mockMvc.perform(post("/api/quiz/practice/items/" + item.getId() + "/answer").with(asAna()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"optionId\": " + optionId + "}"));
    }

    @Test
    void theNearestGrileAndExamplesTheStudentMaySeeComeBackNearestFirst() throws Exception {
        Quiz written = test(false);
        QuizItem source = grila(written, 0, "Calculează $2 + 3 \\cdot 4$.");
        QuizItem near = grila(written, 1, "Calculează $5 + 2 \\cdot 3$.");
        handIn(written, source, near);
        Quiz notYet = test(false); // not written yet: its questions must not be given away
        QuizItem secret = grila(notYet, 0, "Calculează $1 + 2 \\cdot 5$.");
        Quiz practice = test(true);
        QuizItem open = grila(practice, 0, "Calculează $(2 + 3) \\cdot 4$.");
        EnChapterExample example = exampleRepository.save(
                new EnChapterExample(chapter, 1, "Ordinea operațiilor: $5 + 2 \\cdot 3$.", "$5 + 6 = 11$"));
        Quiz other = test(true);
        QuizItem unrelated = grila(other, 0, "Aria pătratului cu latura 3.");
        index(source, vector(0, 0));
        index(near, vector(1, 0.3));     // 0.042
        index(secret, vector(2, 0.35));  // nearer than the example, but not the student's to see
        index(example, vector(3, 0.4));  // 0.072
        index(open, vector(2, 0.5));     // 0.106 (a practice-friendly test, never written)
        index(unrelated, far());         // 1.0: not similar at all

        JsonNode body = similar(source);

        assertThat(body.get("indexed").asBoolean()).isTrue();
        List<String> order = new ArrayList<>();
        for (JsonNode entry : body.get("entries")) {
            order.add(entry.get("kind").asString() + ":" + (entry.get("kind").asString().equals("QUESTION")
                    ? entry.get("question").get("itemId").asLong() : entry.get("example").get("id").asLong()));
        }
        assertThat(order).containsExactly("QUESTION:" + near.getId(), "EXAMPLE:" + example.getId(),
                "QUESTION:" + open.getId());
        JsonNode question = body.get("entries").get(0).get("question");
        assertThat(question.get("statement").asString()).isEqualTo("Calculează $5 + 2 \\cdot 3$.");
        assertThat(question.get("options")).hasSize(2);
        assertThat(question.get("options").get(0).has("correct")).isFalse(); // the answer comes with the check
        JsonNode shown = body.get("entries").get(1).get("example");
        assertThat(shown.get("statement").asString()).startsWith("Ordinea operațiilor");
        assertThat(shown.get("solution").asString()).isEqualTo("$5 + 6 = 11$");
        assertThat(shown.get("chapterId").asLong()).isEqualTo(chapter.getId());
        assertThat(shown.get("lesson").get("chapterCode").asString()).isEqualTo(chapter.getCode());
    }

    @Test
    void neitherTheQuestionItselfNorACopyOfItComesBack() throws Exception {
        Quiz written = test(false);
        QuizItem source = grila(written, 0, "Calculează $2 + 3 \\cdot 4$.");
        handIn(written, source);
        Quiz copy = test(true);
        QuizItem same = grila(copy, 0, "Calculează $2 + 3 \\cdot 4$.");
        index(source, vector(0, 0));
        index(same, vector(0, 0)); // the very same statement in another test: nothing new to practise

        assertThat(similar(source).get("entries")).isEmpty();
    }

    @Test
    void aQuestionNotIndexedYetSaysSo() throws Exception {
        Quiz written = test(false);
        QuizItem source = grila(written, 0, "Calculează $2 + 3 \\cdot 4$.");
        handIn(written, source);

        JsonNode body = similar(source);

        assertThat(body.get("indexed").asBoolean()).isFalse();
        assertThat(body.get("entries")).isEmpty();
    }

    @Test
    void onlyForAQuestionTheStudentHasAnswered() throws Exception {
        Quiz notYet = test(false);
        QuizItem secret = grila(notYet, 0, "Calculează $1 + 2 \\cdot 5$.");
        index(secret, vector(0, 0));

        mockMvc.perform(get("/api/quiz/items/" + secret.getId() + "/similar").with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/quiz/items/" + secret.getId() + "/similar").with(user("prof@scoala.ro").roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSimilarGrilaIsCheckedAtOnceWithItsSolutionAndNothingElseIs() throws Exception {
        Quiz practice = test(true);
        QuizItem open = grila(practice, 0, "Calculează $(2 + 3) \\cdot 4$.");
        Quiz notYet = test(false);
        QuizItem secret = grila(notYet, 0, "Calculează $1 + 2 \\cdot 5$.");

        JsonNode right = json(answer(open, option(open, true)).andExpect(status().isOk()));
        JsonNode wrong = json(answer(open, option(open, false)).andExpect(status().isOk()));
        answer(secret, option(secret, true)).andExpect(status().isConflict());

        assertThat(right.get("correct").asBoolean()).isTrue();
        assertThat(wrong.get("correct").asBoolean()).isFalse();
        assertThat(wrong.get("correctOptionId").asLong()).isEqualTo(option(open, true));
    }
}
