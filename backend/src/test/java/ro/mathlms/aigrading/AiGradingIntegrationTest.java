package ro.mathlms.aigrading;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.ItemRequest;
import ro.mathlms.quiz.OptionRequest;
import ro.mathlms.quiz.PartDtos.PartRequest;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The AI's grade proposals on real Postgres, with the offline fake grader: handing in a paper queues one proposal per
 * photographed answer that has a barem, the worker step fills it in (per requirement), the grading screen reads it,
 * and the teacher can ask again - while the grade itself stays the teacher's.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AiGradingIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AiGradingService service;
    @Autowired private AiGradeProposalRepository proposalRepository;
    @Autowired private QuizAdminService quizAdminService;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizItemRepository itemRepository;

    private User teacher;
    private User student;
    private Quiz quiz;
    private QuizItem grila;
    private QuizItem withBarem;
    private QuizItem withoutBarem;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        teacher = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof", Role.ADMIN));
        student = userRepository.save(new User("elev-" + stamp + "@scoala.ro", "Ana Ionescu", Role.STUDENT));
        while (service.processNext()) {
            // other tests' leftovers must not be read as ours
        }
        quiz = quizAdminService.createQuiz("Simulare EN " + stamp, null, null, null, false);
        quizAdminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.SINGLE_CHOICE, 0, "Cât face 2+2?", 5, null,
                List.of(new OptionRequest(0, "4", true), new OptionRequest(1, "5", false)), null, null, "SUBIECTUL I"));
        quizAdminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.OPEN, 1, "Un teren dreptunghiular.", 0, null,
                null, null, null, "SUBIECTUL al III-lea", null,
                List.of(new PartRequest("Calculează perimetrul.", 2, "$P = 130$ m ... 2p"),
                        new PartRequest("Arată că aria e mai mare.", 3, "$A = 1000$ ... 2p\nConcluzia ... 1p"))));
        quizAdminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.OPEN, 2, "Fără barem.", 4, null, null,
                null, null, "SUBIECTUL al III-lea"));
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quiz.getId());
        grila = items.get(0);
        withBarem = items.get(1);
        withoutBarem = items.get(2);
        quizAdminService.setPublished(quiz.getId(), true);
    }

    private RequestPostProcessor asTeacher() {
        return user(teacher.getEmail()).roles("ADMIN");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** The student photographs both open answers and hands in. */
    private long handIn() {
        long attemptId = attemptService.startAttempt(quiz.getId(), student.getEmail()).attemptId();
        for (QuizItem item : List.of(withBarem, withoutBarem)) {
            attemptService.uploadOpenPhoto(attemptId, item.getId(),
                    new MockMultipartFile("file", "rezolvare.png", "image/png", PNG), student.getEmail());
        }
        attemptService.submit(attemptId, student.getEmail());
        return attemptId;
    }

    @Test
    void handingInQueuesAProposalForEachPhotographedAnswerThatHasABarem() throws Exception {
        long attemptId = handIn();

        assertThat(proposalRepository.findByAttemptIdOrderByItemId(attemptId))
                .extracting(AiGradeProposal::getItemId).containsExactly(withBarem.getId()); // not the grilă, not the one without barem
        JsonNode queued = json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId + "/ai-proposals")
                .with(asTeacher())).andExpect(status().isOk()));
        assertThat(queued.get(0).get("status").asString()).isEqualTo("QUEUED");

        assertThat(service.processNext()).isTrue();

        JsonNode done = json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId + "/ai-proposals")
                .with(asTeacher())).andExpect(status().isOk())).get(0);
        assertThat(done.get("status").asString()).isEqualTo("DONE");
        assertThat(done.get("points").asInt()).isEqualTo(4); // the fake: a) 2/2, b) 2/3
        assertThat(done.get("parts").get(0).get("label").asString()).isEqualTo("a");
        assertThat(done.get("parts").get(1).get("points").asInt()).isEqualTo(2);
        assertThat(done.get("parts").get(1).get("partId").asLong()).isPositive();
        assertThat(done.get("confidence").asString()).isEqualTo("MEDIUM");
        // a proposal is not a grade
        JsonNode paper = json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId).with(asTeacher())));
        assertThat(paper.get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(paper.get("items").get(1).get("awardedPoints").isNull()).isTrue();
    }

    @Test
    void theTeacherCanAskAgainButNotWhereThereIsNoBarem() throws Exception {
        long attemptId = handIn();
        service.processNext();

        JsonNode again = json(mockMvc.perform(post("/api/admin/quiz/attempts/" + attemptId + "/responses/"
                + withBarem.getId() + "/ai-proposal").with(asTeacher()).with(csrf())).andExpect(status().isOk()));
        assertThat(again.get("status").asString()).isEqualTo("QUEUED");
        assertThat(again.get("points").isNull()).isTrue();

        String refused = mockMvc.perform(post("/api/admin/quiz/attempts/" + attemptId + "/responses/"
                        + withoutBarem.getId() + "/ai-proposal").with(asTeacher()).with(csrf()))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("nu are barem");
        mockMvc.perform(post("/api/admin/quiz/attempts/" + attemptId + "/responses/" + grila.getId() + "/ai-proposal")
                .with(asTeacher()).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void anotherTeacherSeesNoProposals() throws Exception {
        long attemptId = handIn();
        User stranger = userRepository.save(new User("teach-" + System.nanoTime() + "@scoala.ro", "Alt", Role.TEACHER));

        mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId + "/ai-proposals")
                .with(user(stranger.getEmail()).roles("TEACHER"))).andExpect(status().isNotFound());
    }
}
