package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Book;
import ro.mathlms.content.BookRepository;
import ro.mathlms.content.Chapter;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.Difficulty;
import ro.mathlms.content.Exercise;
import ro.mathlms.content.ExerciseRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T2 on real Postgres: "Creează quiz din capitol" turns the ticked exercises into a draft the editor opens. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuizFromChapterIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private ExerciseRepository exerciseRepository;

    private SchoolClass schoolClass;
    private Chapter fractii;
    private Exercise first;
    private Exercise second;
    private Exercise otherChapters;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa " + stamp, null));
        Book algebra = bookRepository.save(new Book(schoolClass, "Algebra " + stamp, null));
        fractii = chapterRepository.save(new Chapter(algebra, "Fracții " + stamp, null));
        Chapter procente = chapterRepository.save(new Chapter(algebra, "Procente " + stamp, null));
        first = exerciseRepository.save(new Exercise(fractii, "$\\frac{1}{2}+\\frac{1}{4}$", "$\\frac{3}{4}$", Difficulty.EASY));
        second = exerciseRepository.save(new Exercise(fractii, "Simplifică $\\frac{6}{8}$", null, null));
        otherChapters = exerciseRepository.save(new Exercise(procente, "15% din 200", "30", null));
    }

    private String body(String title, long[] ids, int points) {
        StringBuilder list = new StringBuilder();
        for (long id : ids) {
            list.append(list.isEmpty() ? "" : ",").append(id);
        }
        return "{\"title\":\"" + title + "\",\"exerciseIds\":[" + list + "],\"points\":" + points + "}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theTickedExercisesBecomeADraftQuizOfOpenQuestions() throws Exception {
        String created = mockMvc.perform(post("/api/admin/chapters/" + fractii.getId() + "/quiz").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Fracții — test", new long[]{second.getId(), first.getId()}, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.schoolClassId").value(schoolClass.getId()))
                .andExpect(jsonPath("$.schoolClassName").value(schoolClass.getName()))
                .andExpect(jsonPath("$.chapterId").value(fractii.getId()))
                .andReturn().getResponse().getContentAsString();
        long quizId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/admin/quizzes/" + quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].statement").value("$\\frac{1}{2}+\\frac{1}{4}$"))
                .andExpect(jsonPath("$.items[0].type").value("OPEN"))
                .andExpect(jsonPath("$.items[0].points").value(2))
                .andExpect(jsonPath("$.items[0].solution").value("$\\frac{3}{4}$"))
                .andExpect(jsonPath("$.items[1].statement").value("Simplifică $\\frac{6}{8}$"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anExerciseOfAnotherChapterIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/chapters/" + fractii.getId() + "/quiz").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Amestec", new long[]{first.getId(), otherChapters.getId()}, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void nothingTickedOrNoPointsIsA400() throws Exception {
        mockMvc.perform(post("/api/admin/chapters/" + fractii.getId() + "/quiz").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Gol", new long[]{}, 1)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/chapters/" + fractii.getId() + "/quiz").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Zero", new long[]{first.getId()}, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotBuildAQuiz() throws Exception {
        mockMvc.perform(post("/api/admin/chapters/" + fractii.getId() + "/quiz").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("X", new long[]{first.getId()}, 1)))
                .andExpect(status().isForbidden());
    }
}
