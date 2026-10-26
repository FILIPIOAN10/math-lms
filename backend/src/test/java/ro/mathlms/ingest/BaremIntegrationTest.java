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
import ro.mathlms.quiz.ItemRequest;
import ro.mathlms.quiz.OptionRequest;
import ro.mathlms.quiz.PartDtos.PartRequest;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Barem din poze" on real Postgres, over HTTP, with the offline fake reader: photos are queued and read, the reading
 * comes back mapped onto the test's own questions (I.1, III.1 a), b)...), the teacher applies what they keep, and a
 * test students already took keeps its right answers. Nobody else reads another teacher's reading.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BaremIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BaremService service;
    @Autowired private BaremReadRepository readRepository;
    @Autowired private AiImportProperties importProperties;
    @Autowired private QuizAdminService quizAdminService;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizItemPartRepository partRepository;

    private User teacher;
    private User otherTeacher;
    private Quiz quiz;
    private QuizItem grila;
    private QuizItem problem;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        teacher = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof", Role.ADMIN));
        otherTeacher = userRepository.save(new User("alt-" + stamp + "@scoala.ro", "Alt prof", Role.ADMIN));
        while (service.processNext()) {
            // other tests' leftovers must not be read as ours
        }
        quiz = quizAdminService.createQuiz("Simulare EN " + stamp, null, null, null, false);
        quizAdminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.SINGLE_CHOICE, 0, "Cât face $6 - 6 : 2$?", 5,
                null, List.of(new OptionRequest(0, "0", true), new OptionRequest(1, "3", false),
                new OptionRequest(2, "6", false)), null, null, "SUBIECTUL I"));
        quizAdminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.OPEN, 1, "Un teren dreptunghiular.", 0,
                null, null, null, null, "SUBIECTUL al III-lea", null,
                List.of(new PartRequest("Calculează perimetrul.", 2, null), new PartRequest("Arată că aria e mai mare.", 3, null))));
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quiz.getId());
        grila = items.get(0);
        problem = items.get(1);
    }

    private RequestPostProcessor as(User u) {
        return user(u.getEmail()).roles("ADMIN");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long sendBarem(User who, int photos) throws Exception {
        var request = multipart("/api/admin/quizzes/" + quiz.getId() + "/barem-reads");
        for (int i = 1; i <= photos; i++) {
            request.file(new MockMultipartFile("files", "barem" + i + ".png", "image/png", PNG));
        }
        return json(mockMvc.perform(request.with(as(who)).with(csrf())).andExpect(status().isAccepted())).get("id").asLong();
    }

    private JsonNode reading(User who, long id) throws Exception {
        return json(mockMvc.perform(get("/api/admin/quizzes/" + quiz.getId() + "/barem-reads/" + id).with(as(who)))
                .andExpect(status().isOk()));
    }

    @Test
    void theBaremIsReadInTheBackgroundAndMappedOntoTheTestsQuestions() throws Exception {
        long id = sendBarem(teacher, 2);
        String keys = readRepository.findById(id).orElseThrow().getPageKeys();
        List<Path> photos = objectMapper.readTree(keys).valueStream()
                .map(p -> Path.of(importProperties.pagesDir(), p.get("key").asString())).toList();
        assertThat(photos).hasSize(2).allMatch(Files::exists);
        assertThat(reading(teacher, id).get("status").asString()).isEqualTo("QUEUED");

        assertThat(service.processNext()).isTrue();

        JsonNode done = reading(teacher, id);
        assertThat(done.get("status").asString()).isEqualTo("DONE");
        JsonNode items = done.get("proposal").get("items");
        assertThat(items).hasSize(2);
        JsonNode first = items.get(0);
        assertThat(first.get("label").asString()).isEqualTo("I.1");
        assertThat(first.get("currentLetter").asString()).isEqualTo("a");
        assertThat(first.get("proposedLetter").asString()).isEqualTo("b"); // the fake reader says "b" everywhere
        long optionB = optionRepository.findByItemIdOrderByPosition(grila.getId()).get(1).getId();
        assertThat(first.get("proposedOptionId").asLong()).isEqualTo(optionB);
        JsonNode third = items.get(1);
        assertThat(third.get("label").asString()).isEqualTo("III.1");
        assertThat(third.get("parts").get(0).get("label").asString()).isEqualTo("a");
        assertThat(third.get("parts").get(1).get("proposedBarem").asString()).endsWith("2p"); // 3p in two steps: 1p + 2p
        assertThat(done.get("proposal").get("taken").asBoolean()).isFalse();
        assertThat(photos).noneMatch(Files::exists); // read, then deleted
    }

    @Test
    void theTeacherAppliesWhatTheyKept() throws Exception {
        long optionB = optionRepository.findByItemIdOrderByPosition(grila.getId()).get(1).getId();
        var parts = partRepository.findByItemIdOrderByPosition(problem.getId());
        Map<String, Object> body = Map.of("items", List.of(
                Map.of("itemId", grila.getId(), "correctOptionId", optionB),
                Map.of("itemId", problem.getId(), "parts", List.of(
                        Map.of("partId", parts.get(0).getId(), "barem", "$P = 2(L + l)$ ... 1p\n$P = 130$ m ... 1p"),
                        Map.of("partId", parts.get(1).getId(), "barem", "  ")))));

        mockMvc.perform(put("/api/admin/quizzes/" + quiz.getId() + "/barem").with(as(teacher)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        assertThat(optionRepository.findByItemIdOrderByPosition(grila.getId()).stream().map(QuizOption::isCorrect))
                .containsExactly(false, true, false);
        var saved = partRepository.findByItemIdOrderByPosition(problem.getId());
        assertThat(saved.get(0).getSolution()).isEqualTo("$P = 2(L + l)$ ... 1p\n$P = 130$ m ... 1p");
        assertThat(saved.get(1).getSolution()).isNull(); // blank clears it
    }

    @Test
    void aTestStudentsTookKeepsItsRightAnswersButTakesTheBaremText() throws Exception {
        quizAdminService.setPublished(quiz.getId(), true);
        User student = userRepository.save(new User("elev-" + System.nanoTime() + "@scoala.ro", "Elev", Role.STUDENT));
        attemptService.startAttempt(quiz.getId(), student.getEmail());
        long optionB = optionRepository.findByItemIdOrderByPosition(grila.getId()).get(1).getId();

        String refused = mockMvc.perform(put("/api/admin/quizzes/" + quiz.getId() + "/barem").with(as(teacher)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("items", List.of(Map.of("itemId", grila.getId(), "correctOptionId", optionB))))))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("I.1").contains("nu se mai poate schimba");

        mockMvc.perform(put("/api/admin/quizzes/" + quiz.getId() + "/barem").with(as(teacher)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                Map.of("items", List.of(Map.of("itemId", grila.getId(), "barem", "Răspuns: a) ... 5p"))))))
                .andExpect(status().isNoContent());
        assertThat(itemRepository.findById(grila.getId()).orElseThrow().getSolution()).isEqualTo("Răspuns: a) ... 5p");
        assertThat(optionRepository.findByItemIdOrderByPosition(grila.getId()).get(0).isCorrect()).isTrue();
    }

    @Test
    void anotherTeachersReadingIsNotThere() throws Exception {
        long id = sendBarem(teacher, 1);
        mockMvc.perform(get("/api/admin/quizzes/" + quiz.getId() + "/barem-reads/" + id).with(as(otherTeacher)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesAnythingButPhotosAndAnEmptyTest() throws Exception {
        mockMvc.perform(multipart("/api/admin/quizzes/" + quiz.getId() + "/barem-reads")
                        .file(new MockMultipartFile("files", "barem.pdf", "application/pdf", PNG)).with(as(teacher)).with(csrf()))
                .andExpect(status().isBadRequest());
        Quiz empty = quizAdminService.createQuiz("Gol " + System.nanoTime(), null, null, null, false);
        mockMvc.perform(multipart("/api/admin/quizzes/" + empty.getId() + "/barem-reads")
                        .file(new MockMultipartFile("files", "barem.png", "image/png", PNG)).with(as(teacher)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saysTheFeatureIsOnInTheSettings() throws Exception {
        JsonNode settings = json(mockMvc.perform(get("/api/admin/ai-grading/settings").with(as(teacher))).andExpect(status().isOk()));
        assertThat(settings.get("enabled").asBoolean()).isTrue();
        assertThat(settings.get("baremMaxPages").asInt()).isEqualTo(5);
    }
}
