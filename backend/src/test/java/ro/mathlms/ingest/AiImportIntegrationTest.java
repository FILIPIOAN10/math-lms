package ro.mathlms.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEntryRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.QuizStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Faza 3 on real Postgres, over HTTP, with the offline fake reader: pages are uploaded and queued, the worker step
 * reads them, the teacher reviews and makes a DRAFT quiz (page files then deleted), and nobody else sees any of it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AiImportIntegrationTest {

    /** The smallest valid PNG (1x1): the fake reader ignores what is on the page. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AiImportService service;
    @Autowired private AiImportRepository importRepository;
    @Autowired private AiImportProperties properties;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private AuditEntryRepository auditRepository;

    private User teacher;
    private User otherTeacher;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        teacher = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof", Role.ADMIN));
        otherTeacher = userRepository.save(new User("alt-prof-" + stamp + "@scoala.ro", "Alt prof", Role.ADMIN));
        drainQueue(); // other tests' leftovers must not be read as ours
    }

    private void drainQueue() {
        while (service.processNext()) {
            // empty the shared queue
        }
    }

    private RequestPostProcessor as(User u) {
        return user(u.getEmail()).roles("ADMIN");
    }

    private ResultActions upload(User who, int pages) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/ai-imports");
        for (int i = 1; i <= pages; i++) {
            request.file(new MockMultipartFile("files", "pagina" + i + ".png", "image/png", PNG));
        }
        return mockMvc.perform(request.with(as(who)).with(csrf()));
    }

    private long uploadedId(ResultActions result) throws Exception {
        return json(result.andExpect(status().isAccepted())).get("id").asLong();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private List<Path> pageFiles(long id) throws Exception {
        String keys = importRepository.findById(id).orElseThrow().getPageKeys();
        List<Path> files = new ArrayList<>();
        objectMapper.readTree(keys).forEach(p -> files.add(Path.of(properties.pagesDir(), p.get("key").asString())));
        return files;
    }

    @Test
    void pagesAreQueuedReadReviewedAndTurnedIntoADraftQuiz() throws Exception {
        long id = uploadedId(upload(teacher, 2));
        List<Path> files = pageFiles(id);
        assertThat(files).hasSize(2).allMatch(Files::exists);
        assertThat(json(mockMvc.perform(get("/api/admin/ai-imports/" + id).with(as(teacher))))
                .get("status").asString()).isEqualTo("QUEUED");

        assertThat(service.processNext()).isTrue();

        JsonNode done = json(mockMvc.perform(get("/api/admin/ai-imports/" + id).with(as(teacher))).andExpect(status().isOk()));
        assertThat(done.get("status").asString()).isEqualTo("DONE");
        assertThat(done.get("itemCount").asInt()).isEqualTo(4);
        JsonNode items = done.get("items");
        assertThat(items.get(0).get("type").asString()).isEqualTo("SINGLE_CHOICE");
        assertThat(items.get(0).get("options").size()).isEqualTo(3);
        assertThat(items.get(1).get("hasFigure").asBoolean()).isTrue();
        assertThat(items.get(1).get("warnings").toString()).contains("desen");
        assertThat(items.get(3).get("page").asInt()).isEqualTo(2);
        // the page photos are there for the review, to the uploader only
        mockMvc.perform(get("/api/admin/ai-imports/" + id + "/pages/2").with(as(teacher))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/ai-imports/" + id + "/pages/3").with(as(teacher))).andExpect(status().isNotFound());

        // the teacher keeps the grilă (marking its answer) and the open question, drops the rest
        Map<String, Object> body = Map.of("title", "Din manual " + id, "items", List.of(
                Map.of("type", "SINGLE_CHOICE", "statement", items.get(0).get("statement").asString(), "points", 5,
                        "section", "SUBIECTUL I", "options", List.of(
                                Map.of("position", 0, "text", "5", "correct", false),
                                Map.of("position", 1, "text", "7", "correct", true),
                                Map.of("position", 2, "text", "9", "correct", false))),
                Map.of("type", "OPEN", "statement", items.get(1).get("statement").asString(), "points", 10,
                        "solution", "P = 18 cm", "section", "SUBIECTUL al II-lea", "imageRequired", true)));
        long quizId = json(mockMvc.perform(post("/api/admin/ai-imports/" + id + "/quiz").with(as(teacher)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())).get("quizId").asLong();

        assertThat(quizRepository.findById(quizId).orElseThrow().getStatus()).isEqualTo(QuizStatus.DRAFT);
        var created = itemRepository.findByQuizIdOrderByPosition(quizId);
        assertThat(created).extracting(i -> i.getType()).containsExactly(QuizItemType.SINGLE_CHOICE, QuizItemType.OPEN);
        assertThat(created.get(1).isImageRequired()).isTrue();
        assertThat(created.get(1).getSolution()).isEqualTo("P = 18 cm");
        assertThat(optionRepository.findByItemIdOrderByPosition(created.get(0).getId()))
                .filteredOn(o -> o.isCorrect()).extracting(o -> o.getText()).containsExactly("7");
        // the photos are gone, the import remembers the quiz, the journal says where it came from
        assertThat(files).noneMatch(Files::exists);
        JsonNode after = json(mockMvc.perform(get("/api/admin/ai-imports/" + id).with(as(teacher))));
        assertThat(after.get("quizId").asLong()).isEqualTo(quizId);
        assertThat(after.get("pagesAvailable").asBoolean()).isFalse();
        assertThat(auditRepository.findAll()).anyMatch(e -> e.getAction() == AuditAction.QUIZ_FROM_PHOTOS
                && quizId == e.getEntityId() && "2 întrebări din 2 pagini".equals(e.getDetails()));
        // and a second quiz from the same pages is refused
        mockMvc.perform(post("/api/admin/ai-imports/" + id + "/quiz").with(as(teacher)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    /** A real 2000 x 1000 page: white, with the "drawing" (a black block) where the fake reader says it is. */
    private static byte[] pageWithDrawing() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2000, 1000, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 2000, 1000);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(600, 200, 400, 300);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void theFigureTheAiFoundBecomesTheQuestionsPictureWhenTheQuizIsMade() throws Exception {
        long id = uploadedId(mockMvc.perform(multipart("/api/admin/ai-imports")
                .file(new MockMultipartFile("files", "pagina.png", "image/png", pageWithDrawing()))
                .with(as(teacher)).with(csrf())));
        assertThat(service.processNext()).isTrue();
        JsonNode figureItem = json(mockMvc.perform(get("/api/admin/ai-imports/" + id).with(as(teacher)))).get("items").get(1);
        assertThat(figureItem.get("figurePage").asInt()).isEqualTo(1);
        assertThat(figureItem.get("figureBox").size()).isEqualTo(4);
        assertThat(figureItem.get("warnings").toString()).contains("decupat");

        Map<String, Object> grila = Map.of("type", "SINGLE_CHOICE", "statement", "Cât face 2+3?", "points", 5,
                "options", List.of(Map.of("position", 0, "text", "5", "correct", true), Map.of("position", 1, "text", "6", "correct", false)));
        Map<String, Object> withFigure = Map.of("type", "OPEN", "statement", figureItem.get("statement").asString(),
                "points", 10, "imageRequired", true);
        // a figure on a page that was never sent is refused, and nothing is made
        long quizzesBefore = quizRepository.count();
        mockMvc.perform(post("/api/admin/ai-imports/" + id + "/quiz").with(as(teacher)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("title", "Figuri " + id,
                                "items", List.of(grila, withFigure),
                                "figures", List.of(Map.of("index", 1, "page", 2, "box", List.of(200, 300, 500, 500)))))))
                .andExpect(status().isBadRequest());
        assertThat(quizRepository.count()).isEqualTo(quizzesBefore);

        // the teacher kept the cut (as adjusted: exactly the black block) for the second question
        long quizId = json(mockMvc.perform(post("/api/admin/ai-imports/" + id + "/quiz").with(as(teacher)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("title", "Figuri " + id,
                        "items", List.of(grila, withFigure),
                        "figures", List.of(Map.of("index", 1, "page", 1, "box", List.of(200, 300, 500, 500)))))))
                .andExpect(status().isCreated())).get("quizId").asLong();

        var created = itemRepository.findByQuizIdOrderByPosition(quizId);
        assertThat(created.get(0).getImageKey()).isNull();
        assertThat(created.get(1).getImageKey()).isNotNull();
        assertThat(created.get(1).isImageRequired()).isFalse(); // it has its picture: nothing left to add
        // served like any question picture (an active account; the teacher owns the quiz)
        byte[] picture = mockMvc.perform(get("/api/quiz/items/" + created.get(1).getId() + "/image")
                        .with(user(teacher.getEmail()).authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("STATUS_ACTIVE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage figure = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(picture));
        assertThat(figure.getWidth()).isEqualTo(400);
        assertThat(figure.getHeight()).isEqualTo(300);
        assertThat(pageFiles(id)).isEmpty(); // the page photos are gone, the cut is kept
    }

    @Test
    void aGrilaWithoutItsCorrectAnswerMakesNoQuizAtAll() throws Exception {
        long id = uploadedId(upload(teacher, 1));
        service.processNext();
        long quizzesBefore = quizRepository.count();
        Map<String, Object> body = Map.of("title", "Fără răspuns", "items", List.of(
                Map.of("type", "OPEN", "statement", "Rezolvă", "points", 10),
                Map.of("type", "SINGLE_CHOICE", "statement", "Alege", "points", 5, "options", List.of(
                        Map.of("position", 0, "text", "1", "correct", false),
                        Map.of("position", 1, "text", "2", "correct", false)))));
        mockMvc.perform(post("/api/admin/ai-imports/" + id + "/quiz").with(as(teacher)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertThat(quizRepository.count()).isEqualTo(quizzesBefore);
        assertThat(pageFiles(id)).hasSize(1).allMatch(Files::exists); // still there to try again
    }

    @Test
    void anotherTeacherSeesNothingOfIt() throws Exception {
        long id = uploadedId(upload(teacher, 1));
        mockMvc.perform(get("/api/admin/ai-imports/" + id).with(as(otherTeacher))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/ai-imports/" + id + "/pages/1").with(as(otherTeacher))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/ai-imports/" + id).with(as(otherTeacher)).with(csrf())).andExpect(status().isNotFound());
        assertThat(json(mockMvc.perform(get("/api/admin/ai-imports").with(as(otherTeacher)))).findValues("id")
                .stream().map(JsonNode::asLong)).doesNotContain(id);
        // a student cannot even reach the endpoints
        mockMvc.perform(get("/api/admin/ai-imports/settings").with(user("elev@scoala.ro").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadsAreCheckedAndCountedAgainstTheDailyLimit() throws Exception {
        upload(teacher, 0).andExpect(status().isBadRequest());
        upload(teacher, properties.maxPages() + 1).andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/admin/ai-imports")
                        .file(new MockMultipartFile("files", "x.pdf", "application/pdf", PNG)).with(as(teacher)).with(csrf()))
                .andExpect(status().isBadRequest());

        // fill today's allowance through the repository, then one more page is refused with what is left
        for (int left = properties.dailyPageLimit() - 1; left > 0; left -= 20) { // a row holds at most 20 pages
            importRepository.save(new AiImport(teacher.getId(), null, null, Math.min(left, 20), "[]", Instant.now()));
        }
        JsonNode settings = json(mockMvc.perform(get("/api/admin/ai-imports/settings").with(as(teacher))));
        assertThat(settings.get("enabled").asBoolean()).isTrue();
        assertThat(settings.get("pagesUsedToday").asLong()).isEqualTo(properties.dailyPageLimit() - 1);
        String refused = upload(teacher, 2).andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString();
        assertThat(refused).contains("mai poți trimite doar 1 pagină");
        uploadedId(upload(teacher, 1));
        assertThat(upload(teacher, 1).andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString()).contains("Ai atins limita de azi");
    }

    @Test
    void aFailedReadCanBeTriedAgainAndDiscardingDeletesThePhotos() throws Exception {
        long id = uploadedId(upload(teacher, 1));
        mockMvc.perform(post("/api/admin/ai-imports/" + id + "/retry").with(as(teacher)).with(csrf()))
                .andExpect(status().isConflict()); // only a failed read is retried

        AiImport ai = importRepository.findById(id).orElseThrow();
        ai.start(Instant.now());
        ai.fail("Nu am putut citi paginile.", Instant.now());
        importRepository.save(ai);
        JsonNode failed = json(mockMvc.perform(get("/api/admin/ai-imports/" + id).with(as(teacher))));
        assertThat(failed.get("error").asString()).isEqualTo("Nu am putut citi paginile.");

        mockMvc.perform(post("/api/admin/ai-imports/" + id + "/retry").with(as(teacher)).with(csrf()))
                .andExpect(status().isOk());
        service.processNext();
        assertThat(importRepository.findById(id).orElseThrow().getStatus()).isEqualTo(AiImportStatus.DONE);

        List<Path> files = pageFiles(id);
        mockMvc.perform(delete("/api/admin/ai-imports/" + id).with(as(teacher)).with(csrf())).andExpect(status().isNoContent());
        assertThat(importRepository.findById(id)).isEmpty();
        assertThat(files).noneMatch(Files::exists);
    }

    @Test
    void aReadInterruptedByARestartFailsAndOldPhotosAreDeleted() throws Exception {
        long stuckId = uploadedId(upload(teacher, 1));
        AiImport stuck = importRepository.findById(stuckId).orElseThrow();
        stuck.start(Instant.now().minus(AiImportService.STUCK_AFTER).minusSeconds(60));
        importRepository.save(stuck);
        assertThat(service.recoverStuck()).isGreaterThanOrEqualTo(1);
        AiImport recovered = importRepository.findById(stuckId).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AiImportStatus.FAILED);
        assertThat(recovered.getError()).isEqualTo(AiImportService.INTERRUPTED);

        long oldId = uploadedId(upload(teacher, 1));
        List<Path> files = pageFiles(oldId);
        service.processNext();
        // created long ago: rewrite created_at the way the clock would have
        importRepository.findById(oldId).ifPresent(ai -> {
            org.springframework.test.util.ReflectionTestUtils.setField(ai, "createdAt",
                    Instant.now().minus(java.time.Duration.ofDays(properties.keepPagesDays() + 1)));
            importRepository.save(ai);
        });
        assertThat(service.forgetExpiredPages()).isGreaterThanOrEqualTo(1);
        assertThat(files).noneMatch(Files::exists);
        AiImport old = importRepository.findById(oldId).orElseThrow();
        assertThat(old.hasPages()).isFalse();
        assertThat(old.getItemsJson()).isNotNull(); // the questions stay, only the photos go
    }
}
