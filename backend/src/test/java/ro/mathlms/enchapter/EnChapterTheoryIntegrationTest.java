package ro.mathlms.enchapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.TestcontainersConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * V35 and V49: every seeded EN chapter page starts with theory and worked examples, which a student can read at once
 * (so the "Vezi lecția" link shows on a result) and the teacher can edit like anything they wrote.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterTheoryIntegrationTest {

    private static final RequestPostProcessor STUDENT = user("elev@scoala.ro").authorities(
            new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final List<String> SEEDED = List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10",
            "A11", "A12", "A13", "A14", "A15", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9", "G10");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private JsonNode read(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).with(STUDENT)).andReturn().getResponse().getContentAsString());
    }

    private Map<String, Long> ids() throws Exception {
        Map<String, Long> ids = new HashMap<>();
        for (JsonNode c : read("/api/en-chapters")) {
            ids.put(c.get("code").asString(), c.get("id").asLong());
        }
        return ids;
    }

    @Test
    void everySeededChapterStartsWithTheoryAndWorkedExamples() throws Exception {
        Map<String, Long> ids = ids();
        for (String code : SEEDED) {
            JsonNode page = read("/api/en-chapters/" + ids.get(code));
            assertThat(page.get("notes").asString()).as(code).hasSizeGreaterThan(500);
            assertThat(page.get("examples")).as(code).hasSizeGreaterThanOrEqualTo(4);
            page.get("examples").forEach(e -> {
                assertThat(e.get("statement").asString()).as(code).isNotBlank();
                assertThat(e.get("solution").asString()).as(code).isNotBlank();
            });
        }

        JsonNode a1 = read("/api/en-chapters/" + ids.get("A1"));
        assertThat(a1.get("notes").asString()).startsWith("Ordinea efectuării operațiilor");
        assertThat(a1.get("examples").get(0).get("statement").asString()).isEqualTo("Calculează $30 - 18 : 3$.");
        assertThat(a1.get("examples").get(0).get("solution").asString()).contains("Rezultat: $24$");
    }

    /** V50: a wrong answer can say what to revise from the first day, for every question already written. */
    @Test
    void everySeededChapterSaysWhatAWrongAnswerSendsTheStudentToRevise() throws Exception {
        Map<String, Long> ids = ids();
        for (String code : SEEDED) {
            assertThat(read("/api/en-chapters/" + ids.get(code)).get("revisionTopic").asString()).as(code).isNotBlank();
        }

        assertThat(read("/api/en-chapters/" + ids.get("G1")).get("revisionTopic").asString())
                .isEqualTo("simetrie și segmente");
    }

    /** V50: S II.6 asks how many vertices, edges and faces a solid has. */
    @Test
    void theSolidsPageCountsVerticesEdgesAndFaces() throws Exception {
        assertThat(read("/api/en-chapters/" + ids().get("G8")).get("notes").asString())
                .contains("\n\nVârfuri, muchii și fețe\n", "$3n$ muchii").endsWith("$4$ fețe.");
    }

    /** V49: what the EN programme asks for comes after what a page already had, in the order it was written. */
    @Test
    void theExistingPagesGetTheProgrammeTopicsAfterWhatTheyHad() throws Exception {
        JsonNode a1 = read("/api/en-chapters/" + ids().get("A1"));

        assertThat(a1.get("notes").asString()).startsWith("Ordinea efectuării operațiilor")
                .contains("Atenție\n", "\n\nÎmpărțirea cu rest\n").endsWith("$398 + 203 \\approx 400 + 200 = 600$.");
        assertThat(a1.get("examples")).hasSize(8);
        assertThat(a1.get("examples").get(4).get("statement").asString()).isEqualTo("Calculează $2^5 \\cdot 2^3 : 2^6$.");
        assertThat(a1.get("examples").get(5).get("statement").asString()).contains("deîmpărțitul");
        assertThat(a1.get("examples").get(7).get("statement").asString()).contains("baza 2");
    }

    /** The programme says what is taught, not where it lands in the paper. */
    @Test
    void theNewChaptersHaveNoAutomaticLinkToAnExamPosition() {
        Long linked = jdbc.queryForObject("SELECT count(*) FROM en_chapter_slots s JOIN en_chapters c ON c.id = s.chapter_id"
                + " WHERE c.code IN ('A12', 'A13', 'A14', 'A15', 'G10')", Long.class);

        assertThat(linked).isZero();
    }

    /** The same statements on pages a teacher has changed; the transaction rolls back at the end of the test. */
    @Test
    @Transactional
    void aPageATeacherChangedKeepsWhatTheyDid() throws Exception {
        jdbc.update("UPDATE en_chapters SET notes = NULL WHERE code = 'G1'");
        jdbc.update("DELETE FROM en_chapter_examples WHERE chapter_id = (SELECT id FROM en_chapters WHERE code = 'G1')");
        jdbc.update("UPDATE en_chapters SET notes = 'Textul profesorului' WHERE code = 'A1'");
        jdbc.update("UPDATE en_chapters SET title = 'Capitolul meu' WHERE code = 'A12'");
        long a12Examples = examples("A12");

        jdbc.execute(new ClassPathResource("db/migration/V49__en_programme_2026.sql").getContentAsString(StandardCharsets.UTF_8));

        assertThat(notes("G1")).isNull();
        assertThat(examples("G1")).isZero();
        assertThat(notes("A1")).startsWith("Textul profesorului\n\nÎmpărțirea cu rest");
        assertThat(jdbc.queryForObject("SELECT title FROM en_chapters WHERE code = 'A12'", String.class)).isEqualTo("Capitolul meu");
        assertThat(examples("A12")).isEqualTo(a12Examples);
    }

    private String notes(String code) {
        return jdbc.queryForObject("SELECT notes FROM en_chapters WHERE code = ?", String.class, code);
    }

    private long examples(String code) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM en_chapter_examples e JOIN en_chapters c ON c.id = e.chapter_id"
                + " WHERE c.code = ?", Long.class, code);
        return count == null ? 0 : count;
    }
}
