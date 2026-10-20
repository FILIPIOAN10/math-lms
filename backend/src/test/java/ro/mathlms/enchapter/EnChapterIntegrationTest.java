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
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEntryRepository;
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
 * The EN chapter pages on real Postgres + the local file store: the 20 chapters seeded by V33, the teacher editing the
 * list and filling a page (theory photos, worked examples with an optional figure), every active account reading it,
 * and nobody else writing it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterIntegrationTest {

    /** A real 1x1 PNG, so the stored file is an image. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditEntryRepository auditRepository;

    private RequestPostProcessor teacher;
    private RequestPostProcessor student;
    private RequestPostProcessor parent;
    private RequestPostProcessor pendingStudent;
    private String stamp;

    private static RequestPostProcessor as(User u, boolean active) {
        SimpleGrantedAuthority role = new SimpleGrantedAuthority("ROLE_" + u.getRole());
        return active
                ? user(u.getEmail()).authorities(role, new SimpleGrantedAuthority("STATUS_ACTIVE"))
                : user(u.getEmail()).authorities(role);
    }

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        teacher = as(userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.ADMIN)), true);
        student = as(userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT)), true);
        parent = as(userRepository.save(new User("maria." + stamp + "@scoala.ro", "Maria", Role.PARENT)), true);
        pendingStudent = as(userRepository.save(new User("nou." + stamp + "@scoala.ro", "Nou", Role.STUDENT)), false);
    }

    /** A code no other test uses: the list is shared by every test in the run. */
    private String code() {
        return "T" + stamp.substring(stamp.length() - 6);
    }

    private String json(RequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private long createChapter(String code, String title) throws Exception {
        String body = json(post("/api/admin/en-chapters").with(csrf()).with(teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"title\":\"" + title + "\",\"area\":\"ALGEBRA\"}"), 201);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "materie.png", "image/png", PNG);
    }

    private List<String> codesInOrder() throws Exception {
        return JsonPath.read(json(get("/api/en-chapters").with(student), 200), "$[*].code");
    }

    @Test
    void everyActiveAccountReadsTheTwentySeededChaptersInOrder() throws Exception {
        List<String> codes = codesInOrder();

        assertThat(codes).contains("A1", "A11", "G1", "G9");
        assertThat(codes.indexOf("A1")).isLessThan(codes.indexOf("A2"));
        assertThat(codes.indexOf("A11")).isLessThan(codes.indexOf("G1"));
        mockMvc.perform(get("/api/en-chapters").with(parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'A1')].title").value("Ordinea efectuării operațiilor"))
                .andExpect(jsonPath("$[?(@.code == 'G9')].area").value("GEOMETRY"));
        mockMvc.perform(get("/api/en-chapters").with(pendingStudent)).andExpect(status().isForbidden());
    }

    @Test
    void theTeacherAddsRenamesAndDeletesAnEmptyChapterAndEachStepIsJournalled() throws Exception {
        String code = code();
        long id = createChapter(code, "Puteri");

        mockMvc.perform(put("/api/admin/en-chapters/" + id).with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"title\":\"Puteri și radicali\",\"area\":\"ALGEBRA\","
                                + "\"notes\":\"$a^m \\\\cdot a^n = a^{m+n}$\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Puteri și radicali"));
        mockMvc.perform(get("/api/en-chapters/" + id).with(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("$a^m \\cdot a^n = a^{m+n}$"))
                .andExpect(jsonPath("$.photos").isEmpty())
                .andExpect(jsonPath("$.examples").isEmpty());

        mockMvc.perform(delete("/api/admin/en-chapters/" + id).with(csrf()).with(teacher)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/en-chapters/" + id).with(student)).andExpect(status().isNotFound());

        assertThat(auditRepository.findAll())
                .filteredOn(e -> e.getEntityId() != null && e.getEntityId() == id)
                .extracting(e -> e.getAction())
                .contains(AuditAction.EN_CHAPTER_CREATED, AuditAction.EN_CHAPTER_UPDATED, AuditAction.EN_CHAPTER_DELETED);
    }

    @Test
    void aSecondChapterWithTheSameCodeIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/en-chapters").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"a1\",\"title\":\"Altceva\",\"area\":\"ALGEBRA\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("A1")));
        mockMvc.perform(post("/api/admin/en-chapters").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"A 1\",\"title\":\"Altceva\",\"area\":\"ALGEBRA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheTeacherChangesThePages() throws Exception {
        mockMvc.perform(post("/api/admin/en-chapters").with(csrf()).with(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"Z9\",\"title\":\"x\",\"area\":\"ALGEBRA\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/en-chapters/1/photos").file(png()).with(csrf()).with(parent))
                .andExpect(status().isForbidden());
    }

    @Test
    void aChapterMovesUpAndDownTheList() throws Exception {
        long first = createChapter(code(), "Primul");
        String other = "U" + stamp.substring(stamp.length() - 6);
        long second = createChapter(other, "Al doilea");
        List<String> before = codesInOrder();
        assertThat(before.indexOf(code())).isLessThan(before.indexOf(other));

        mockMvc.perform(post("/api/admin/en-chapters/" + second + "/move?direction=UP").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());

        List<String> after = codesInOrder();
        assertThat(after.indexOf(other)).isLessThan(after.indexOf(code()));

        mockMvc.perform(post("/api/admin/en-chapters/" + second + "/move?direction=DOWN").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        List<String> back = codesInOrder();
        assertThat(back.indexOf(code())).isLessThan(back.indexOf(other));

        mockMvc.perform(delete("/api/admin/en-chapters/" + first).with(csrf()).with(teacher)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/en-chapters/" + second).with(csrf()).with(teacher)).andExpect(status().isNoContent());
    }

    @Test
    void aChapterMovesOnlyAmongItsOwnPart() throws Exception {
        long algebra = createChapter(code(), "Algebră la final");
        String geometryCode = "V" + stamp.substring(stamp.length() - 6);
        String body = json(post("/api/admin/en-chapters").with(csrf()).with(teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + geometryCode + "\",\"title\":\"Geometrie la final\",\"area\":\"GEOMETRY\"}"), 201);
        long geometry = ((Number) JsonPath.read(body, "$.id")).longValue();

        // Up from the end of geometry: past the last geometry chapter (G9), not past the algebra one added before it.
        mockMvc.perform(post("/api/admin/en-chapters/" + geometry + "/move?direction=UP").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        List<String> codes = codesInOrder();
        assertThat(codes.indexOf(geometryCode)).isLessThan(codes.indexOf("G9"));

        mockMvc.perform(post("/api/admin/en-chapters/" + geometry + "/move?direction=DOWN").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/en-chapters/" + geometry).with(csrf()).with(teacher)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/en-chapters/" + algebra).with(csrf()).with(teacher)).andExpect(status().isNoContent());
    }

    @Test
    void theTeacherPhotographsTheTheoryAndEveryAccountSeesIt() throws Exception {
        long id = createChapter(code(), "Cu poze");

        String body = json(multipart("/api/admin/en-chapters/" + id + "/photos").file(png()).with(csrf()).with(teacher), 201);
        long photo = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/en-chapters/" + id).with(student))
                .andExpect(jsonPath("$.photos[0].id").value(photo));
        mockMvc.perform(get("/api/en-chapters").with(student))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].photoCount").value(1));
        mockMvc.perform(get("/api/en-chapters/" + id + "/photos/" + photo).with(student))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/en-chapters/" + id + "/photos/" + photo).with(parent)).andExpect(status().isOk());
        // A photo is only reachable through its own chapter.
        mockMvc.perform(get("/api/en-chapters/1/photos/" + photo).with(student)).andExpect(status().isNotFound());

        // Not an image: refused before anything is stored.
        mockMvc.perform(multipart("/api/admin/en-chapters/" + id + "/photos")
                        .file(new MockMultipartFile("file", "notite.txt", "text/plain", "x".getBytes()))
                        .with(csrf()).with(teacher))
                .andExpect(status().isBadRequest());

        // While the page has content it cannot be deleted by mistake.
        mockMvc.perform(delete("/api/admin/en-chapters/" + id).with(csrf()).with(teacher))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("poz")));

        mockMvc.perform(delete("/api/admin/en-chapters/" + id + "/photos/" + photo).with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/en-chapters/" + id + "/photos/" + photo).with(student)).andExpect(status().isNotFound());
        assertThat(auditRepository.findAll()).extracting(e -> e.getAction())
                .contains(AuditAction.EN_CHAPTER_PHOTO_ADDED, AuditAction.EN_CHAPTER_PHOTO_REMOVED);
        mockMvc.perform(delete("/api/admin/en-chapters/" + id).with(csrf()).with(teacher)).andExpect(status().isNoContent());
    }

    @Test
    void theTeacherWritesWorkedExamplesWithAnOptionalFigure() throws Exception {
        long id = createChapter(code(), "Cu modele");

        String body = json(post("/api/admin/en-chapters/" + id + "/examples").with(csrf()).with(teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statement\":\"$30 - 18 : 3$\",\"solution\":\"$= 30 - 6 = 24$\"}"), 201);
        long example = ((Number) JsonPath.read(body, "$.id")).longValue();
        json(post("/api/admin/en-chapters/" + id + "/examples").with(csrf()).with(teacher)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statement\":\"$2^3 + 3 \\\\cdot 2^2$\",\"solution\":\"$= 20$\"}"), 201);

        // A worked example needs its explained solution.
        mockMvc.perform(post("/api/admin/en-chapters/" + id + "/examples").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"$1+1$\",\"solution\":\" \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/en-chapters/" + id + "/examples/" + example).with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"$30 - 18 : 3$\",\"solution\":\"Întâi împărțirea: $18 : 3 = 6$, apoi $30 - 6 = 24$.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/admin/en-chapters/" + id + "/examples/" + example + "/image").file(png())
                        .with(r -> { r.setMethod("PUT"); return r; }).with(csrf()).with(teacher))
                .andExpect(status().isNoContent());

        String page = json(get("/api/en-chapters/" + id).with(student), 200);
        assertThat(JsonPath.<List<String>>read(page, "$.examples[*].statement"))
                .containsExactly("$30 - 18 : 3$", "$2^3 + 3 \\cdot 2^2$");
        assertThat(JsonPath.<String>read(page, "$.examples[0].solution")).startsWith("Întâi împărțirea");
        assertThat(JsonPath.<String>read(page, "$.examples[0].image")).isNotBlank();
        mockMvc.perform(get("/api/en-chapters/" + id + "/examples/" + example + "/image").with(student))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(delete("/api/admin/en-chapters/" + id + "/examples/" + example + "/image").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/en-chapters/" + id + "/examples/" + example + "/image").with(student))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/en-chapters/" + id + "/examples/" + example).with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/en-chapters/" + id).with(student))
                .andExpect(jsonPath("$.examples.length()").value(1));
        assertThat(auditRepository.findAll()).extracting(e -> e.getAction())
                .contains(AuditAction.EN_EXAMPLE_ADDED, AuditAction.EN_EXAMPLE_UPDATED, AuditAction.EN_EXAMPLE_DELETED);
    }

    @Test
    void anUnknownChapterIsNotFound() throws Exception {
        mockMvc.perform(get("/api/en-chapters/999999999").with(student)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/en-chapters/999999999").with(csrf()).with(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"Q1\",\"title\":\"x\",\"area\":\"ALGEBRA\"}"))
                .andExpect(status().isNotFound());
    }
}
