package ro.mathlms.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.QuizStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T3 on real Postgres: "Publică și dă ca temă" is all or nothing. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublishAndAssignIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    private SchoolClass clasa;
    private SchoolClass alta;
    private Quiz draft;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        clasa = schoolClassRepository.save(new SchoolClass("Clasa a 8-a " + stamp, null));
        alta = schoolClassRepository.save(new SchoolClass("Clasa a 9-a " + stamp, null));
        draft = quizRepository.save(new Quiz("Simulare " + stamp, null));
        itemRepository.save(new QuizItem(draft, 1, QuizItemType.OPEN, "Rezolvă", 5, null));
    }

    private ResultActions publishAndAssign(Long quizId, Long classId, Instant dueAt) throws Exception {
        return mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/publish-and-assign").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schoolClassId\":" + classId + ",\"dueAt\":\"" + dueAt + "\"}"));
    }

    private QuizStatus statusOf(Quiz quiz) {
        return quizRepository.findById(quiz.getId()).orElseThrow().getStatus();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void publishesTheDraftAndGivesItAsHomeworkInOneStep() throws Exception {
        publishAndAssign(draft.getId(), clasa.getId(), Instant.now().plus(2, ChronoUnit.DAYS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quizId").value(draft.getId()))
                .andExpect(jsonPath("$.schoolClassName").value(clasa.getName()));

        assertThat(statusOf(draft)).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(assignmentRepository.existsByQuizIdAndSchoolClassId(draft.getId(), clasa.getId())).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aRefusedHomeworkLeavesTheQuizADraft() throws Exception {
        publishAndAssign(draft.getId(), clasa.getId(), Instant.now().minus(1, ChronoUnit.HOURS))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(draft)).isEqualTo(QuizStatus.DRAFT);
        assertThat(assignmentRepository.existsByQuizIdAndSchoolClassId(draft.getId(), clasa.getId())).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aQuizForAnotherClassIsRefusedAndStaysADraft() throws Exception {
        Quiz forClasa = draft;
        forClasa.assignToClass(clasa);
        quizRepository.save(forClasa);

        publishAndAssign(draft.getId(), alta.getId(), Instant.now().plus(1, ChronoUnit.DAYS))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(draft)).isEqualTo(QuizStatus.DRAFT);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aQuizWithoutQuestionsCannotBePublished() throws Exception {
        Quiz empty = quizRepository.save(new Quiz("Gol " + System.nanoTime(), null));

        publishAndAssign(empty.getId(), clasa.getId(), Instant.now().plus(1, ChronoUnit.DAYS))
                .andExpect(status().isBadRequest());

        assertThat(statusOf(empty)).isEqualTo(QuizStatus.DRAFT);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotPublish() throws Exception {
        publishAndAssign(draft.getId(), clasa.getId(), Instant.now().plus(1, ChronoUnit.DAYS))
                .andExpect(status().isForbidden());
    }
}
