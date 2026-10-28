package ro.mathlms.tutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.ItemRequest;
import ro.mathlms.quiz.OptionRequest;
import ro.mathlms.quiz.PartDtos.PartRequest;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemHintRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Sugerează indicii (AI)" on real Postgres, with the tutor service replaced: what the tutor is asked (the question as
 * text, the exam position only when it names the chapter), what the editor gets back, who may ask, and that asking
 * changes nothing in the question.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.tutor.enabled=true", "app.tutor.token=test-token"})
@AutoConfigureMockMvc
class TutorHintIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizAdminService quizAdminService;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizItemHintRepository hintRepository;
    @MockitoBean private TutorClient tutor;

    private User teacher;

    @BeforeEach
    void setUp() {
        teacher = userRepository.save(new User("prof-" + System.nanoTime() + "@scoala.ro", "Prof", Role.ADMIN));
    }

    private RequestPostProcessor asTeacher() {
        return user(teacher.getEmail()).roles("ADMIN");
    }

    private ResultActions ask(Long itemId, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(post("/api/admin/quiz-items/" + itemId + "/tutor-hints").with(who).with(csrf()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private List<QuizItem> quiz(ItemRequest... items) {
        Quiz quiz = quizAdminService.createQuiz("Simulare EN " + System.nanoTime(), null, null, null, false);
        for (ItemRequest item : items) {
            quizAdminService.addItem(quiz.getId(), item);
        }
        return itemRepository.findByQuizIdOrderByPosition(quiz.getId());
    }

    private static TutorClient.Answer threeLevels() {
        TutorClient.Source page = new TutorClient.Source(1, "AIS5P5", "Ordinea operațiilor", 12);
        return new TutorClient.Answer(true, List.of(
                new TutorClient.Step(1, "Întâi paranteza.", List.of(page)),
                new TutorClient.Step(2, "$7 - 2 = 5$, apoi înmulțirea.", List.of(page)),
                new TutorClient.Step(3, "$20 - 10 = 10$.", List.of(page))),
                null, "slot", null, 1500, 400, 0.00035);
    }

    @Test
    void anExamQuestionGoesWithItsOptionsAndItsPositionAndComesBackAsThreeLevels() throws Exception {
        QuizItem grila = quiz(new ItemRequest(QuizItemType.SINGLE_CHOICE, 0, "Rezultatul calculului $20 - 2(7 - 2)$ este:",
                5, null, List.of(new OptionRequest(0, "10", true), new OptionRequest(1, "90", false)), null, null,
                "SUBIECTUL I")).getFirst();
        when(tutor.help(any(), any(), anyInt())).thenReturn(threeLevels());

        JsonNode body = json(ask(grila.getId(), asTeacher()).andExpect(status().isOk()));

        verify(tutor).help("Rezultatul calculului $20 - 2(7 - 2)$ este:\nVariante: a) 10; b) 90", "I.1", 3);
        assertThat(body.get("answered").asBoolean()).isTrue();
        assertThat(body.get("levels")).hasSize(3);
        assertThat(body.get("levels").get(1).get("text").asString()).isEqualTo("$7 - 2 = 5$, apoi înmulțirea.");
        assertThat(body.get("levels").get(0).get("sources").get(0).get("title").asString()).isEqualTo("Ordinea operațiilor");
        assertThat(body.get("levels").get(0).get("sources").get(0).get("bookPage").asInt()).isEqualTo(12);
        assertThat(body.get("refusal").isNull()).isTrue();
        assertThat(body.get("costUsd").asDouble()).isEqualTo(0.00035);
        // a proposal is not an edit
        assertThat(hintRepository.findByItemIdOrderByPosition(grila.getId())).isEmpty();
        assertThat(itemRepository.findById(grila.getId()).orElseThrow().getSolution()).isNull();
    }

    @Test
    void aQuestionWithoutAnExamPositionIsSearchedByMeaningWithItsRequirements() throws Exception {
        QuizItem open = quiz(new ItemRequest(QuizItemType.OPEN, 0, "Un teren dreptunghiular are $L = 40$ m.", 0, null,
                null, null, null, null, null,
                List.of(new PartRequest("Calculează perimetrul.", 2, "... 2p"),
                        new PartRequest("Calculează aria.", 3, "... 3p")))).getFirst();
        when(tutor.help(any(), isNull(), anyInt())).thenReturn(new TutorClient.Answer(false, List.of(),
                new TutorClient.Refusal("too_far", "Nu am asta în manual."), "vector", 0.61, 0, 0, 0.0));

        JsonNode body = json(ask(open.getId(), asTeacher()).andExpect(status().isOk()));

        verify(tutor).help(eq("Un teren dreptunghiular are $L = 40$ m.\na) Calculează perimetrul.\nb) Calculează aria."),
                isNull(), eq(3));
        assertThat(body.get("answered").asBoolean()).isFalse();
        assertThat(body.get("levels")).isEmpty();
        assertThat(body.get("refusal").asString()).isEqualTo("Nu am asta în manual.");
        assertThat(body.get("refusalReason").asString()).isEqualTo("too_far");
    }

    @Test
    void whenTheTutorCannotAnswerTheTeacherGetsItsMessage() throws Exception {
        QuizItem item = quiz(new ItemRequest(QuizItemType.OPEN, 0, "Rezolvă $2x = 6$.", 4, null, null)).getFirst();
        when(tutor.help(any(), any(), anyInt())).thenThrow(new TutorException(TutorClient.UNREACHABLE));

        String message = ask(item.getId(), asTeacher()).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(message).isEqualTo(TutorClient.UNREACHABLE);
    }

    @Test
    void anotherTeachersQuestionIsNotFoundAndTheTutorIsNotAsked() throws Exception {
        QuizItem item = quiz(new ItemRequest(QuizItemType.OPEN, 0, "Rezolvă $2x = 6$.", 4, null, null)).getFirst();
        User stranger = userRepository.save(new User("teach-" + System.nanoTime() + "@scoala.ro", "Alt", Role.TEACHER));

        ask(item.getId(), user(stranger.getEmail()).roles("TEACHER")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/quiz-items/" + item.getId() + "/tutor-hints")
                .with(user("elev@scoala.ro").roles("STUDENT")).with(csrf())).andExpect(status().isForbidden());

        verify(tutor, never()).help(any(), any(), anyInt());
    }

    @Test
    void theEditorCanAskWhetherTheTutorIsOn() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/api/admin/tutor/settings").with(asTeacher())).andExpect(status().isOk()));

        assertThat(body.get("enabled").asBoolean()).isTrue();
    }
}
