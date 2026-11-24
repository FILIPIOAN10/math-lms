package ro.mathlms.enchapter;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEntryRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V78: the new lesson of a chapter, written and checked by the tutor service (tutor.store_lesson). The teacher reviews
 * it claim by claim, may edit it, and approves it; a student reads it only once it is approved AND the chapter shown.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterWrittenLessonIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditEntryRepository auditRepository;
    @Autowired private JdbcTemplate jdbc;

    private RequestPostProcessor teacher;
    private RequestPostProcessor student;
    private String stamp;
    private final List<Long> created = new ArrayList<>();

    private static RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole()),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        teacher = as(userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.ADMIN)));
        student = as(userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT)));
    }

    @AfterEach
    void removeChapters() {
        created.forEach(id -> jdbc.update("DELETE FROM en_chapters WHERE id = ?", id));
        created.clear();
    }

    /** A chapter with no book photo (nothing holds "Arată-o elevilor" back) and a stored lesson. */
    private long chapterWithLesson(String suffix, String status, int red, int yellow) {
        long id = jdbc.queryForObject("INSERT INTO en_chapters (code, title, area, position) VALUES (?, 'Lecție', "
                + "'ALGEBRA', 999) RETURNING id", Long.class, "W" + stamp.substring(stamp.length() - 6) + suffix);
        created.add(id);
        jdbc.update("INSERT INTO en_chapter_lessons (chapter_id, markdown, claims_json, sources_json, status, red_count, "
                        + "yellow_count, model) VALUES (?, ?, ?, ?, ?, ?, ?, 'gemini-x')", id,
                "## Ideea pe scurt\n\nAdunăm $2 + 3 = 5$.",
                "[{\"id\":\"idea.1\",\"where\":\"Ideea pe scurt\",\"text\":\"Adunăm $2 + 3 = 5$.\",\"colour\":\"green\","
                        + "\"reasons\":[],\"sourceIds\":[\"EN-1\"]}]",
                "[{\"id\":\"EN-1\",\"kind\":\"EN\",\"label\":\"Culegere EN, pagina 3\",\"text\":\"2 + 3 = 5\"}]",
                status, red, yellow);
        return id;
    }

    private String url(long id) {
        return "/api/en-chapters/" + id + "/written-lesson";
    }

    private void approve(long id, boolean readYellow, RequestPostProcessor who, int expected) throws Exception {
        mockMvc.perform(post("/api/admin/en-chapters/" + id + "/written-lesson/approve").with(csrf()).with(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"readYellow\":" + readYellow + "}"))
                .andExpect(status().is(expected));
    }

    private void show(long id) throws Exception {
        mockMvc.perform(put("/api/admin/en-chapters/" + id + "/lesson").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void theTeacherReviewsEveryClaimWithItsSourcesAndAStudentSeesNothingYet() throws Exception {
        long id = chapterWithLesson("A", "draft", 0, 0);

        mockMvc.perform(get(url(id)).with(teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.claims[0].id").value("idea.1"))
                .andExpect(jsonPath("$.claims[0].colour").value("green"))
                .andExpect(jsonPath("$.sources[0].label").value("Culegere EN, pagina 3"))
                .andExpect(jsonPath("$.markdown").value("## Ideea pe scurt\n\nAdunăm $2 + 3 = 5$."));
        mockMvc.perform(get(url(id)).with(student))
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.markdown").doesNotExist())
                .andExpect(jsonPath("$.claims").doesNotExist());
    }

    @Test
    void aLessonWithARedClaimCannotBeApprovedAndYellowMustBeRead() throws Exception {
        long flagged = chapterWithLesson("B", "flagged", 1, 0);
        approve(flagged, true, teacher, 409);

        long yellow = chapterWithLesson("C", "draft", 0, 2);
        approve(yellow, false, teacher, 400);
        approve(yellow, true, student, 403);
        approve(yellow, true, teacher, 204);

        mockMvc.perform(get(url(yellow)).with(teacher))
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());
        assertThat(auditRepository.findAll()).extracting(e -> e.getAction()).contains(AuditAction.EN_LESSON_APPROVED);
    }

    @Test
    void aStudentReadsItOnlyApprovedAndWithTheChapterShown() throws Exception {
        long id = chapterWithLesson("D", "draft", 0, 0);

        show(id);
        mockMvc.perform(get(url(id)).with(student)).andExpect(jsonPath("$.exists").value(false));

        approve(id, false, teacher, 204);
        mockMvc.perform(get(url(id)).with(student))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.markdown").value("## Ideea pe scurt\n\nAdunăm $2 + 3 = 5$."))
                .andExpect(jsonPath("$.claims").doesNotExist())
                .andExpect(jsonPath("$.sources").doesNotExist());

        jdbc.update("UPDATE en_chapters SET lesson_published = FALSE WHERE id = ?", id);
        mockMvc.perform(get(url(id)).with(student)).andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void anEditIsTheTeachersTextTakesTheApprovalAwayAndMayThenBeApprovedDespiteOldColours() throws Exception {
        long id = chapterWithLesson("E", "flagged", 1, 0);

        mockMvc.perform(put("/api/admin/en-chapters/" + id + "/written-lesson").with(csrf()).with(student)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"markdown\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/en-chapters/" + id + "/written-lesson").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"markdown\":\"## Ideea\\n\\nCorectat.\"}"))
                .andExpect(status().isNoContent());

        String body = mockMvc.perform(get(url(id)).with(teacher)).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(body, "$.editedByTeacher")).isTrue();
        assertThat((String) JsonPath.read(body, "$.markdown")).isEqualTo("## Ideea\n\nCorectat.");
        approve(id, false, teacher, 204);  // the red claim described the old text: the new one is the teacher's

        mockMvc.perform(put("/api/admin/en-chapters/" + id + "/written-lesson").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"markdown\":\"Altă variantă.\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url(id)).with(teacher)).andExpect(jsonPath("$.status").value("draft"));
        mockMvc.perform(put("/api/admin/en-chapters/" + id + "/written-lesson").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"markdown\":\"  \"}"))
                .andExpect(status().isBadRequest());
        assertThat(auditRepository.findAll()).extracting(e -> e.getAction()).contains(AuditAction.EN_LESSON_EDITED);
    }

    @Test
    void aChapterWithoutANewLessonSaysSo() throws Exception {
        long id = jdbc.queryForObject("INSERT INTO en_chapters (code, title, area, position) VALUES (?, 'Fără', "
                + "'ALGEBRA', 999) RETURNING id", Long.class, "V" + stamp.substring(stamp.length() - 6));
        created.add(id);

        mockMvc.perform(get(url(id)).with(teacher)).andExpect(jsonPath("$.exists").value(false));
        approve(id, true, teacher, 404);
        mockMvc.perform(get(url(999_999_999L)).with(teacher)).andExpect(status().isNotFound());
    }
}
