package ro.mathlms.enchapter;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
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
 * V73: the book's pages written out (each photo's text with the app's own drawings of its figures, filled by
 * tutor/scripts/publish_lessons.py). The teacher reads them first; students and parents only once the teacher shows
 * them, like every other text the AI wrote.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterLessonIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditEntryRepository auditRepository;
    @Autowired private JdbcTemplate jdbc;

    private RequestPostProcessor teacher;
    private RequestPostProcessor student;
    private RequestPostProcessor parent;
    private String stamp;
    private final java.util.List<Long> created = new java.util.ArrayList<>();

    /**
     * The chapter list is shared by every test of the run (EnChapterIntegrationTest moves chapters around the end of
     * it), so the chapters made here go away again, with their photos, text and drawings (ON DELETE CASCADE).
     */
    @org.junit.jupiter.api.AfterEach
    void removeChapters() {
        created.forEach(id -> jdbc.update("DELETE FROM en_chapters WHERE id = ?", id));
        created.clear();
    }

    private static RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole()),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        teacher = as(userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.ADMIN)));
        student = as(userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT)));
        parent = as(userRepository.save(new User("maria." + stamp + "@scoala.ro", "Maria", Role.PARENT)));
    }

    private String json(RequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private long chapter(String suffix) throws Exception {
        String code = "L" + stamp.substring(stamp.length() - 5) + suffix;
        String body = json(post("/api/admin/en-chapters").with(csrf()).with(teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"title\":\"Lecție\",\"area\":\"GEOMETRY\"}"), 201);
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        created.add(id);
        return id;
    }

    private long photo(long chapterId) throws Exception {
        String body = json(multipart("/api/admin/en-chapters/" + chapterId + "/photos")
                .file(new MockMultipartFile("file", "pagina.png", "image/png", PNG)).with(csrf()).with(teacher), 201);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** What the import writes for one page: a drawing, then the page's text pointing at it. */
    private long written(long photoId, String text) {
        Long drawing = jdbc.queryForObject("INSERT INTO en_photo_drawings (photo_id, number, description, png) "
                + "VALUES (?, 1, 'Triunghiul ABC', ?) RETURNING id", Long.class, photoId, PNG);
        jdbc.update("INSERT INTO en_photo_lessons (photo_id, markdown) VALUES (?, ?)",
                photoId, text + "\n\n![Triunghiul ABC](desen:" + drawing + ")");
        return drawing;
    }

    private void show(long chapterId, boolean published, RequestPostProcessor who, int expected) throws Exception {
        mockMvc.perform(put("/api/admin/en-chapters/" + chapterId + "/lesson").with(csrf()).with(who)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"published\":" + published + "}"))
                .andExpect(status().is(expected));
    }

    @Test
    void theTeacherReadsTheWrittenOutPagesBeforeAnyStudent() throws Exception {
        long id = chapter("A");
        long photo = photo(id);
        long drawing = written(photo, "## Teorema lui Pitagora\n$a^2 + b^2 = c^2$");

        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.pages[0].photoId").value(photo))
                .andExpect(jsonPath("$.pages[0].markdown").value(org.hamcrest.Matchers.containsString("desen:" + drawing)));
        mockMvc.perform(get("/api/en-chapters/" + id + "/drawings/" + drawing).with(teacher))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(student))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.pages").isEmpty());
        mockMvc.perform(get("/api/en-chapters/" + id + "/drawings/" + drawing).with(student)).andExpect(status().isNotFound());
    }

    @Test
    void onceShownEveryAccountReadsItAndHidingItAgainIsJournalled() throws Exception {
        long id = chapter("B");
        long drawing = written(photo(id), "Text");

        show(id, true, teacher, 204);
        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(student))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.pages.length()").value(1));
        mockMvc.perform(get("/api/en-chapters/" + id + "/drawings/" + drawing).with(parent)).andExpect(status().isOk());

        show(id, false, teacher, 204);
        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(student)).andExpect(jsonPath("$.pages").isEmpty());
        assertThat(auditRepository.findAll()).extracting(e -> e.getAction())
                .contains(AuditAction.EN_CHAPTER_LESSON_SHOWN, AuditAction.EN_CHAPTER_LESSON_HIDDEN);
    }

    @Test
    void onlyTheTeacherShowsOrHidesIt() throws Exception {
        long id = chapter("C");

        show(id, true, student, 403);
        show(id, true, parent, 403);
        show(999_999_999L, true, teacher, 404);
    }

    @Test
    void eachPageSaysWhichPageOfTheBookItIs() throws Exception {
        long id = chapter("G");
        long photo = photo(id);
        written(photo, "Text");
        jdbc.update("UPDATE en_photo_lessons SET book_page = 144 WHERE photo_id = ?", photo);

        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(teacher))
                .andExpect(jsonPath("$.pages[0].bookPage").value(144));
    }

    @Test
    void v74FillsTheBookPageFromTheTranscriptHeader() {
        // What V74 ran on the pages already written: the same statement, on a page written before it.
        long id = jdbc.queryForObject("INSERT INTO en_chapters (code, title, area, position) VALUES (?, 'V74', 'ALGEBRA', 999) "
                + "RETURNING id", Long.class, "W" + stamp.substring(stamp.length() - 6));
        created.add(id);
        long photo = jdbc.queryForObject("INSERT INTO en_chapter_photos (chapter_id, position, image_key) VALUES (?, 1, 'x.jpg') "
                + "RETURNING id", Long.class, id);
        jdbc.update("INSERT INTO tutor_photo_transcripts (photo_id, content, transcribed_with) VALUES (?, ?, 'test')",
                photo, "---\nsource_pdf: A1\nbook_page: 37\n---\n\nText");
        jdbc.update("INSERT INTO en_photo_lessons (photo_id, markdown) VALUES (?, 'Text')", photo);

        jdbc.update("UPDATE en_photo_lessons l SET book_page = substring(t.content from 'book_page:[ \t]*([0-9]+)')::INT "
                + "FROM tutor_photo_transcripts t WHERE t.photo_id = l.photo_id AND l.photo_id = ?", photo);

        assertThat(jdbc.queryForObject("SELECT book_page FROM en_photo_lessons WHERE photo_id = ?", Integer.class, photo))
                .isEqualTo(37);
    }

    @Test
    void pagesComeInBookOrderAndAPageWithoutTextIsLeftOut() throws Exception {
        long id = chapter("D");
        long first = photo(id);
        photo(id);  // a photo the import has not written out yet
        long third = photo(id);
        written(third, "A treia");
        written(first, "Prima");

        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(teacher))
                .andExpect(jsonPath("$.pages.length()").value(2))
                .andExpect(jsonPath("$.pages[0].photoId").value(first))
                .andExpect(jsonPath("$.pages[1].photoId").value(third));
    }

    // --- V77: nothing half-done or unchecked reaches a student ---

    @Test
    void theTeacherSeesHowMuchIsReadyAndCannotShowItBeforeEveryPageIsWrittenAndChecked() throws Exception {
        long id = chapter("H");
        long first = photo(id);
        long second = photo(id);
        written(first, "Prima");

        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(teacher))
                .andExpect(jsonPath("$.photoCount").value(2))
                .andExpect(jsonPath("$.pages.length()").value(1))
                .andExpect(jsonPath("$.needsReview").value(0));
        show(id, true, teacher, 409);

        written(second, "A doua, cu $[?]$");
        jdbc.update("UPDATE en_photo_lessons SET needs_review = TRUE, review_note = 'text necitit: [?]' WHERE photo_id = ?",
                second);
        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(teacher))
                .andExpect(jsonPath("$.needsReview").value(1))
                .andExpect(jsonPath("$.pages[1].needsReview").value(true))
                .andExpect(jsonPath("$.pages[1].reviewNote").value("text necitit: [?]"));
        show(id, true, teacher, 409);

        mockMvc.perform(post("/api/admin/en-chapters/" + id + "/lesson/pages/" + second + "/reviewed").with(csrf()).with(student))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/en-chapters/" + id + "/lesson/pages/" + second + "/reviewed").with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        show(id, true, teacher, 204);
        show(id, false, teacher, 204);  // hiding is always allowed
        assertThat(auditRepository.findAll()).extracting(e -> e.getAction())
                .contains(AuditAction.EN_CHAPTER_LESSON_PAGE_REVIEWED);
    }

    @Test
    void aPageMarkedForReviewAfterTheChapterWasShownIsNotServedToStudents() throws Exception {
        long id = chapter("I");
        long good = photo(id);
        long bad = photo(id);
        written(good, "Bună");
        written(bad, "Rea");
        show(id, true, teacher, 204);
        jdbc.update("UPDATE en_photo_lessons SET needs_review = TRUE WHERE photo_id = ?", bad);

        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(student))
                .andExpect(jsonPath("$.pages.length()").value(1))
                .andExpect(jsonPath("$.pages[0].photoId").value(good));
    }

    @Test
    void textTheAiWroteOnAPageStaysHiddenFromStudentsUntilTheChapterIsShown() throws Exception {
        long id = chapter("J");
        String sectionBody = json(post("/api/admin/en-chapters").with(csrf()).with(teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"L" + stamp.substring(stamp.length() - 5) + "JS\",\"title\":\"Secțiune\",\"area\":\"GEOMETRY\","
                        + "\"parentId\":" + id + ",\"notes\":\"scris de profesor\"}"), 201);
        long section = ((Number) JsonPath.read(sectionBody, "$.id")).longValue();
        created.add(0, section);
        // What tutor.build_chapter writes: the text and its MD5, so the backend can tell it is the AI's, still unedited.
        jdbc.update("UPDATE en_chapters SET notes = 'scris de AI', notes_generated_md5 = md5('scris de AI') WHERE id = ?", id);

        mockMvc.perform(get("/api/en-chapters/" + id).with(student))
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.sections[0].notes").value("scris de profesor"));
        mockMvc.perform(get("/api/en-chapters/" + id).with(teacher)).andExpect(jsonPath("$.notes").value("scris de AI"));
        jdbc.update("UPDATE en_chapters SET notes = 'scris de AI', notes_generated_md5 = md5('scris de AI') WHERE id = ?", section);
        mockMvc.perform(get("/api/en-chapters/" + section).with(parent)).andExpect(jsonPath("$.notes").doesNotExist());

        show(id, true, teacher, 204);
        mockMvc.perform(get("/api/en-chapters/" + id).with(student))
                .andExpect(jsonPath("$.notes").value("scris de AI"))
                .andExpect(jsonPath("$.sections[0].notes").value("scris de AI"));
    }

    @Test
    void v77SendsAPageWithUnreadTextToReview() {
        long id = jdbc.queryForObject("INSERT INTO en_chapters (code, title, area, position) VALUES (?, 'V77', 'ALGEBRA', 999) "
                + "RETURNING id", Long.class, "X" + stamp.substring(stamp.length() - 6));
        created.add(id);
        long photo = jdbc.queryForObject("INSERT INTO en_chapter_photos (chapter_id, position, image_key) VALUES (?, 1, 'x.jpg') "
                + "RETURNING id", Long.class, id);
        jdbc.update("INSERT INTO en_photo_lessons (photo_id, markdown) VALUES (?, 'Avem $x = [?]$')", photo);

        // The backfill V77 ran on the pages already written.
        jdbc.update("UPDATE en_photo_lessons SET needs_review = TRUE, review_note = 'text necitit: [?]' "
                + "WHERE markdown LIKE '%[?]%' AND photo_id = ?", photo);

        assertThat(jdbc.queryForObject("SELECT needs_review FROM en_photo_lessons WHERE photo_id = ?", Boolean.class, photo))
                .isTrue();
    }

    @Test
    void aDrawingIsReachedOnlyThroughItsOwnChapterAndGoesWithItsPhoto() throws Exception {
        long id = chapter("E");
        long other = chapter("F");
        long photo = photo(id);
        long drawing = written(photo, "Text");
        show(id, true, teacher, 204);

        mockMvc.perform(get("/api/en-chapters/" + other + "/drawings/" + drawing).with(teacher)).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/en-chapters/" + id + "/photos/" + photo).with(csrf()).with(teacher))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/en-chapters/" + id + "/lesson").with(teacher)).andExpect(jsonPath("$.pages").isEmpty());
        mockMvc.perform(get("/api/en-chapters/" + id + "/drawings/" + drawing).with(teacher)).andExpect(status().isNotFound());
    }
}
