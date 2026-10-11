package ro.mathlms.quiz;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** EN sections on real Postgres: a question keeps its heading through save, edit and copy. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class QuizSectionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private QuizRepository quizRepository;

    /** The heading as JSON text: two lines, the second the instruction under it. */
    private static final String HEADING_JSON = "SUBIECTUL I\\nÎncercuiește litera corespunzătoare răspunsului corect.";
    private static final String HEADING = "SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.";

    private static String item(String sectionJson) {
        return "{\"type\":\"OPEN\",\"position\":0,\"statement\":\"Rezolvă\",\"points\":5"
                + (sectionJson == null ? "" : ",\"section\":\"" + sectionJson + "\"") + "}";
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void theHeadingIsKeptThroughSaveEditAndCopy() throws Exception {
        Long quizId = quizRepository.save(new Quiz("EN " + System.nanoTime(), null)).getId();

        String created = mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(item(HEADING_JSON)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section").value(HEADING))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(put("/api/admin/quiz-items/" + idOf(created)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(item("SUBIECTUL al II-lea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.section").value("SUBIECTUL al II-lea"));

        String copy = mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/copy").with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/admin/quizzes/" + idOf(copy)))
                .andExpect(jsonPath("$.items[0].section").value("SUBIECTUL al II-lea"));
    }

    @Test
    void noHeadingIsNull() throws Exception {
        Long quizId = quizRepository.save(new Quiz("Fără " + System.nanoTime(), null)).getId();

        mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(item(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section").doesNotExist());
    }
}
