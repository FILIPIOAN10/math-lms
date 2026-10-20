package ro.mathlms.enchapter;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A chapter holds its sections and a section its points, so a student opens one chapter (all its pages) and jumps to a
 * section by the page it starts on. Sections have no photos of their own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterSectionsIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EnChapterService service;

    /** The list is shared by every test of the run, so each test gets its own codes. */
    private static final java.util.concurrent.atomic.AtomicInteger SEQUENCE = new java.util.concurrent.atomic.AtomicInteger(1000);

    private RequestPostProcessor teacher;
    private RequestPostProcessor student;
    private String stamp;
    private int suffix;

    private static RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole()),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    @BeforeEach
    void setUp() {
        suffix = SEQUENCE.incrementAndGet();
        stamp = String.valueOf(System.nanoTime());
        teacher = as(userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.ADMIN)));
        student = as(userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT)));
    }

    /** Codes are at most ten characters: a letter + four digits leaves room for "S1P2"; one number per test. */
    private String root(String letter) {
        return letter + suffix;
    }

    private String json(RequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private long create(String code, String title, String area, Long parentId, Integer bookPage, int expectedStatus)
            throws Exception {
        String body = "{\"code\":\"" + code + "\",\"title\":\"" + title + "\",\"area\":\"" + area + "\""
                + (parentId == null ? "" : ",\"parentId\":" + parentId)
                + (bookPage == null ? "" : ",\"bookPage\":" + bookPage) + "}";
        String response = json(post("/api/admin/en-chapters").with(csrf()).with(teacher)
                .contentType(MediaType.APPLICATION_JSON).content(body), expectedStatus);
        return expectedStatus == 201 ? ((Number) JsonPath.read(response, "$.id")).longValue() : -1;
    }

    private long create(String code, String title, Long parentId, Integer bookPage) throws Exception {
        return create(code, title, "ALGEBRA", parentId, bookPage, 201);
    }

    private List<String> codesInOrder() throws Exception {
        return JsonPath.read(json(get("/api/en-chapters").with(student), 200), "$[*].code");
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "p.png", "image/png", PNG);
    }

    @Test
    void aSectionHangsUnderItsChapterAndKnowsThePageItStartsOn() throws Exception {
        long chapter = create(root("R"), "Numere naturale", null, 9);
        long section = create(root("R") + "S5", "Puterea", chapter, 12);

        mockMvc.perform(get("/api/en-chapters").with(student))
                .andExpect(jsonPath("$[?(@.id == " + section + ")].parentId").value((int) chapter))
                .andExpect(jsonPath("$[?(@.id == " + section + ")].bookPage").value(12))
                .andExpect(jsonPath("$[?(@.id == " + chapter + ")].bookPage").value(9));

        mockMvc.perform(get("/api/en-chapters/" + chapter).with(student))
                .andExpect(jsonPath("$.rootId").value((int) chapter))
                .andExpect(jsonPath("$.sections.length()").value(1))
                .andExpect(jsonPath("$.sections[0].id").value((int) section))
                .andExpect(jsonPath("$.sections[0].bookPage").value(12));
        // Asking for the section itself says which chapter page to open.
        mockMvc.perform(get("/api/en-chapters/" + section).with(student))
                .andExpect(jsonPath("$.rootId").value((int) chapter))
                .andExpect(jsonPath("$.parentId").value((int) chapter))
                .andExpect(jsonPath("$.bookPage").value(12));
    }

    @Test
    void theChapterPageCarriesTheWrittenTheoryOfEachSection() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);
        json(post("/api/admin/en-chapters").with(csrf()).with(teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + root("R") + "S1\",\"title\":\"Adunarea\",\"area\":\"ALGEBRA\","
                        + "\"parentId\":" + chapter + ",\"bookPage\":9,\"notes\":\"Dacă $a+b=s$, atunci $s$ e suma.\"}"), 201);
        create(root("R") + "S2", "Scăderea", chapter, 10); // no text yet

        mockMvc.perform(get("/api/en-chapters/" + chapter).with(student))
                .andExpect(jsonPath("$.sections[0].notes").value("Dacă $a+b=s$, atunci $s$ e suma."))
                .andExpect(jsonPath("$.sections[1].notes").doesNotExist());
    }

    @Test
    void aSectionHasPointsButNothingDeeper() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);
        long section = create(root("R") + "S1", "Secțiune", chapter, 9);
        long point = create(root("R") + "S1P1", "Punct", section, 10);

        mockMvc.perform(get("/api/en-chapters/" + chapter).with(student))
                .andExpect(jsonPath("$.sections.length()").value(2));
        mockMvc.perform(get("/api/en-chapters/" + point).with(student))
                .andExpect(jsonPath("$.rootId").value((int) chapter));

        mockMvc.perform(post("/api/admin/en-chapters").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + root("R") + "S1P1X\",\"title\":\"Prea adânc\",\"area\":\"ALGEBRA\","
                                + "\"parentId\":" + point + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Capitol, secțiune, punct")));
    }

    @Test
    void aSectionStaysInThePartOfItsChapter() throws Exception {
        long chapter = create(root("R"), "Algebră", null, 9);

        create(root("R") + "S1", "Geometrie", "GEOMETRY", chapter, 9, 400);
    }

    @Test
    void aMissingParentIsRefused() throws Exception {
        create(root("R"), "Orfan", "ALGEBRA", 999999999L, 9, 400);
    }

    @Test
    void aChapterCannotBecomeASectionOfItsOwnSection() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);
        long section = create(root("R") + "S1", "Secțiune", chapter, 9);

        mockMvc.perform(put("/api/admin/en-chapters/" + chapter).with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + root("R") + "\",\"title\":\"Capitol\",\"area\":\"ALGEBRA\","
                                + "\"parentId\":" + section + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aChapterWithSectionsIsNotDeletedByMistake() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);
        long section = create(root("R") + "S1", "Secțiune", chapter, 9);

        mockMvc.perform(delete("/api/admin/en-chapters/" + chapter).with(csrf()).with(teacher))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("subcapitol")));

        mockMvc.perform(delete("/api/admin/en-chapters/" + section).with(csrf()).with(teacher)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/en-chapters/" + chapter).with(csrf()).with(teacher)).andExpect(status().isNoContent());
    }

    @Test
    void movingAChapterTakesItsSectionsWithIt() throws Exception {
        long first = create(root("R"), "Primul", null, 9);
        create(root("R") + "S1", "Secțiunea primului", first, 9);
        long second = create(root("Q"), "Al doilea", null, 30);
        create(root("Q") + "S1", "Secțiunea celui de-al doilea", second, 30);

        mockMvc.perform(post("/api/admin/en-chapters/" + second + "/move?direction=UP").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());

        List<String> codes = codesInOrder().stream()
                .filter(c -> c.startsWith(root("R")) || c.startsWith(root("Q"))).toList();
        assertThat(codes).containsExactly(root("Q"), root("Q") + "S1", root("R"), root("R") + "S1");
    }

    @Test
    void aSectionOfAChapterWithAPageIsReady() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);
        long section = create(root("R") + "S1", "Secțiune", chapter, 9);
        assertThat(service.readyIds()).doesNotContain(chapter, section);

        json(multipart("/api/admin/en-chapters/" + chapter + "/photos").file(png()).with(csrf()).with(teacher), 201);

        assertThat(service.readyIds()).contains(chapter, section);
    }

    @Test
    void aWholeChaptersTheoryFitsInThePageText() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);
        String chapterOfTheory = "Teorie cu formule $a^2 + b^2$. ".repeat(1500); // about 45,000 characters

        mockMvc.perform(put("/api/admin/en-chapters/" + chapter).with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + root("R") + "\",\"title\":\"Capitol\",\"area\":\"ALGEBRA\",\"bookPage\":9,"
                                + "\"notes\":\"" + chapterOfTheory.replace("\\", "\\\\") + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aTextThatIsTooLongIsRefusedWithAMessageTheTeacherCanRead() throws Exception {
        long chapter = create(root("R"), "Capitol", null, 9);

        mockMvc.perform(put("/api/admin/en-chapters/" + chapter).with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + root("R") + "\",\"title\":\"Capitol\",\"area\":\"ALGEBRA\","
                                + "\"notes\":\"" + "x".repeat(EnChapter.MAX_NOTES + 1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Textul paginii poate avea cel mult")));
    }

    @Test
    void aChapterTakesFortyPagesBecauseALongChapterIsOnePdf() throws Exception {
        long chapter = create(root("R"), "Lung", null, 1);
        for (int i = 0; i < 40; i++) {
            json(multipart("/api/admin/en-chapters/" + chapter + "/photos").file(png()).with(csrf()).with(teacher), 201);
        }

        mockMvc.perform(multipart("/api/admin/en-chapters/" + chapter + "/photos").file(png()).with(csrf()).with(teacher))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("40")));
    }
}
