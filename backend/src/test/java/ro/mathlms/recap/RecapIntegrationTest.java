package ro.mathlms.recap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.Assignment;
import ro.mathlms.assignment.AssignmentRepository;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.QuizStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „Teme recapitulative” on real Postgres, over HTTP: the zip import (idempotent, statements as sent, figures stored,
 * EN chapter recognised), the teacher's review (nothing unapproved goes in a test; a missing drawing blocks approval),
 * a draft test made of approved exercises, and catch-up homework for the students weak on an EN chapter.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RecapIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private RecapExerciseRepository repository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizItemPartRepository partRepository;
    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private EnChapterRepository enChapterRepository;
    @Autowired private TransactionTemplate tx;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String stamp;
    private User admin;
    private SchoolClass schoolClass;
    private User ana;
    private User bob;

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        admin = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof", Role.ADMIN));
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa " + stamp, null));
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        bob = userRepository.save(new User("bob." + stamp + "@scoala.ro", "Bob Rus", Role.STUDENT));
        enrollmentRepository.save(new Enrollment(ana, schoolClass));
        enrollmentRepository.save(new Enrollment(bob, schoolClass));
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getEmail()).roles("ADMIN");
    }

    private String key(int number) {
        return "X" + stamp + "-" + number;
    }

    /** Three exercises: a percent one with two checked requirements, a cube with its drawing, a triangle whose
     * drawing was not found. */
    private byte[] zip() throws Exception {
        List<Map<String, Object>> exercises = List.of(
                row("key", key(1), "tema", 3, "number", 1,
                        "statement", "Un preț de 200 lei crește cu 10%.",
                        "solution", "Calculăm procentul.", "aiStatus", "AGREE", "verified", true,
                        "parts", List.of(
                                Map.of("label", "a", "statement", "Aflați creșterea.", "solution", "10% din 200 = 20",
                                        "answer", "20 lei", "result", "20", "verified", true),
                                Map.of("label", "b", "statement", "Aflați prețul nou.", "solution", "200 + 20 = 220",
                                        "answer", "220 lei", "result", "220", "verified", true)),
                        "figureMissing", false),
                row("key", key(2), "tema", 12, "number", 2,
                        "statement", "Cubul $ABCDA'B'C'D'$ are latura de 6 cm. Aflați volumul cubului.",
                        "solution", "V = 6^3", "answer", "216 cm^3", "result", "216", "verified", true,
                        "aiStatus", "AGREE", "parts", List.of(), "figure", "figures/" + key(2) + ".png",
                        "figureMissing", false),
                row("key", key(3), "tema", 9, "number", 3,
                        "statement", "Triunghiul $ABC$ din figură are $AB = 3$. Arătați că e isoscel.",
                        "aiStatus", "PROOF", "verified", false, "parts", List.of(), "figureMissing", true));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("culegere.json"));
            zip.write(objectMapper.writeValueAsString(Map.of("exercises", exercises)).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("figures/" + key(2) + ".png"));
            zip.write(png());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    private static byte[] png() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(40, 30,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private ResultActions upload(RequestPostProcessor who) throws Exception {
        return mockMvc.perform(multipart("/api/admin/recap/import")
                .file(new MockMultipartFile("file", "teme-culegere.zip", "application/zip", zip()))
                .with(who).with(csrf()));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private RecapExercise byKey(int number) {
        return tx.execute(s -> {
            RecapExercise found = repository.findBySourceKey(key(number)).orElseThrow();
            org.hibernate.Hibernate.initialize(found.getParts()); // read after the transaction
            return found;
        });
    }

    /**
     * Audit 2026-11-27 M1: the picture a teacher adds must really be one - a web page that claims to be a PNG is
     * refused - and a real one is stored under the format it is, whatever its name.
     */
    @Test
    void aTeachersPictureMustReallyBeOneAndIsStoredAsWhatItIs() throws Exception {
        upload(asAdmin()).andExpect(status().isOk());
        long id = byKey(3).getId();

        mockMvc.perform(multipart("/api/admin/recap/exercises/" + id + "/image")
                        .file(new MockMultipartFile("file", "desen.html", "image/png",
                                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(byKey(3).getImageKey()).isNull();

        mockMvc.perform(multipart("/api/admin/recap/exercises/" + id + "/image")
                        .file(new MockMultipartFile("file", "desen.html", "image/png", png()))
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isOk());
        assertThat(byKey(3).getImageKey()).endsWith(".png");
        mockMvc.perform(get("/api/admin/recap/exercises/" + id + "/image").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(org.springframework.http.MediaType.IMAGE_PNG));
    }

    private ResultActions approve(long id) throws Exception {
        return mockMvc.perform(post("/api/admin/recap/exercises/" + id + "/review").with(asAdmin()).with(csrf())
                .contentType("application/json").content("{\"review\":\"APPROVED\"}"));
    }

    @Test
    void theImportAddsNewExercisesOnlyAndRecognisesTheirChapter() throws Exception {
        JsonNode first = json(upload(asAdmin()).andExpect(status().isOk()));
        assertThat(first.get("added").asInt()).isEqualTo(3);

        RecapExercise percent = byKey(1);
        assertThat(percent.getReview()).isEqualTo(RecapReview.DRAFT);
        assertThat(percent.getParts()).extracting(RecapPart::getResult).containsExactly("20", "220");
        String chapter = tx.execute(s -> repository.findById(percent.getId()).orElseThrow().getEnChapter().getCode());
        assertThat(chapter).isEqualTo("A3");
        RecapExercise cube = byKey(2);
        assertThat(cube.getImageKey()).isNotNull();
        mockMvc.perform(get("/api/admin/recap/exercises/" + cube.getId() + "/image").with(asAdmin()))
                .andExpect(status().isOk());
        assertThat(byKey(3).isFigureMissing()).isTrue();

        // the teacher edits one, then the same zip comes again: nothing added, the edit stays
        mockMvc.perform(put("/api/admin/recap/exercises/" + percent.getId()).with(asAdmin()).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of(
                                "statement", "Un preț de 300 lei crește cu 10%.", "solution", "",
                                "answer", "", "result", "",
                                "parts", List.of(Map.of("statement", "Aflați creșterea.", "solution", "30",
                                                "answer", "30 lei", "result", "30"),
                                        Map.of("statement", "Aflați prețul nou.", "solution", "330",
                                                "answer", "330 lei", "result", "330"))))))
                .andExpect(status().isOk());
        JsonNode again = json(upload(asAdmin()).andExpect(status().isOk()));
        assertThat(again.get("added").asInt()).isZero();
        assertThat(byKey(1).getStatement()).startsWith("Un preț de 300");

        // only an admin loads the book; a student does not even see the list
        upload(user("prof2-" + stamp + "@scoala.ro").roles("TEACHER")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/recap/exercises").with(user(ana.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyApprovedExercisesGoInATestAndAMissingDrawingBlocksApproval() throws Exception {
        upload(asAdmin()).andExpect(status().isOk());
        long percent = byKey(1).getId();
        long cube = byKey(2).getId();
        long triangle = byKey(3).getId();

        // a draft cannot go in a test
        mockMvc.perform(post("/api/admin/recap/quizzes").with(asAdmin()).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", "Recapitulare",
                                "exerciseIds", List.of(percent)))))
                .andExpect(status().isConflict());

        approve(percent).andExpect(status().isOk());
        approve(cube).andExpect(status().isOk());
        approve(triangle).andExpect(status().isConflict()); // its drawing is missing
        mockMvc.perform(post("/api/admin/recap/exercises/" + triangle + "/no-figure").with(asAdmin()).with(csrf()))
                .andExpect(status().isOk());
        approve(triangle).andExpect(status().isOk());

        // the list filters by review state and EN chapter
        JsonNode approved = json(mockMvc.perform(get("/api/admin/recap/exercises").param("review", "APPROVED")
                .param("q", "X" + stamp).with(asAdmin())).andExpect(status().isOk()));
        assertThat(approved).hasSize(3);

        JsonNode created = json(mockMvc.perform(post("/api/admin/recap/quizzes").with(asAdmin()).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of(
                                "title", "Recapitulare " + stamp, "exerciseIds", List.of(cube, percent),
                                "schoolClassId", schoolClass.getId()))))
                .andExpect(status().isCreated()));
        long quizId = created.get("quizId").asLong();
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.DRAFT);
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId);
        assertThat(items).extracting(QuizItem::getType).containsOnly(QuizItemType.OPEN);
        assertThat(items.get(0).getStatement()).contains("Cubul"); // in the order chosen
        assertThat(items.get(0).getImageKey()).isNotNull().isNotEqualTo(byKey(2).getImageKey()); // its own copy
        assertThat(items.get(0).getSolution()).contains("216 cm^3");
        List<QuizItemPart> parts = partRepository.findByItemIdOrderByPosition(items.get(1).getId());
        assertThat(parts).extracting(QuizItemPart::getFinalAnswer).containsExactly("20", "220");
        assertThat(parts.get(1).getSolution()).contains("220 lei");

        // the list says where each was used
        JsonNode one = json(mockMvc.perform(get("/api/admin/recap/exercises/" + cube).with(asAdmin()))
                .andExpect(status().isOk()));
        assertThat(one.get("usedIn").asInt()).isEqualTo(1);
    }

    /** One exercise of "metoda mersului invers" (A13) that cites a drawing, with or without the drawing in the zip. */
    private byte[] figureZip(boolean withDrawing) throws Exception {
        Map<String, Object> exercise = row("key", key(4), "tema", 7, "number", 4,
                "statement", "Rezolvați prin metoda mersului invers problema din figura 4.",
                "answer", "12", "result", "12", "verified", true, "aiStatus", "AGREE", "parts", List.of(),
                "figureMissing", !withDrawing);
        if (withDrawing) {
            exercise.put("figure", "figures/" + key(4) + ".png");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("culegere.json"));
            zip.write(objectMapper.writeValueAsString(Map.of("exercises", List.of(exercise))).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (withDrawing) {
                zip.putNextEntry(new ZipEntry("figures/" + key(4) + ".png"));
                zip.write(png());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private JsonNode load(byte[] zip) throws Exception {
        return json(mockMvc.perform(multipart("/api/admin/recap/import")
                        .file(new MockMultipartFile("file", "teme-culegere.zip", "application/zip", zip))
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isOk()));
    }

    @Test
    void anApprovedExerciseWithoutItsDrawingStaysOutOfTestsUntilAReloadBringsTheDrawing() throws Exception {
        load(figureZip(false));
        long id = byKey(4).getId();
        // approved before anyone noticed the missing drawing (the server's state that V83 flags)
        tx.executeWithoutResult(s -> {
            RecapExercise e = repository.findById(id).orElseThrow();
            e.review(RecapReview.APPROVED, admin.getId(), Instant.now());
            e.markFigureMissing(true);
        });
        EnChapter a13 = enChapterRepository.findByCode("A13").orElseThrow();
        String due = Instant.now().plus(3, ChronoUnit.DAYS).toString();

        mockMvc.perform(post("/api/admin/recap/quizzes").with(asAdmin()).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", "Fără desen", "exerciseIds", List.of(id)))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/recap/catch-up").with(asAdmin()).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("schoolClassId", schoolClass.getId(),
                                "enChapterId", a13.getId(), "studentIds", List.of(ana.getId()), "count", 1, "dueAt", due))))
                .andExpect(status().isConflict());

        // the same key comes again, now with its drawing: only the drawing is added, the approval stays
        JsonNode reload = load(figureZip(true));
        assertThat(reload.get("added").asInt()).isZero();
        assertThat(reload.get("figuresAdded").asInt()).isEqualTo(1);
        RecapExercise fixed = byKey(4);
        assertThat(fixed.getImageKey()).isNotNull();
        assertThat(fixed.isFigureMissing()).isFalse();
        assertThat(fixed.getReview()).isEqualTo(RecapReview.APPROVED);
        mockMvc.perform(post("/api/admin/recap/quizzes").with(asAdmin()).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", "Cu desen", "exerciseIds", List.of(id)))))
                .andExpect(status().isCreated());

        // a drawing already there is never replaced by a reload
        assertThat(load(figureZip(true)).get("figuresAdded").asInt()).isZero();
        assertThat(byKey(4).getImageKey()).isEqualTo(fixed.getImageKey());
    }

    @Test
    void migrationV83FlagsExercisesWhoseTextCitesADrawingTheyDoNotHave() throws Exception {
        load(figureZip(false));
        long cites = byKey(4).getId();
        upload(asAdmin()).andExpect(status().isOk());
        long plain = byKey(1).getId();
        long withNote = byKey(2).getId(); // has its drawing: never flagged
        tx.executeWithoutResult(s -> {
            repository.findById(cites).orElseThrow().waiveFigure(); // as if the import had missed it (group C)
            RecapExercise note = repository.findById(plain).orElseThrow();
            note.write(note.getStatement() + "\n<!-- warning: No figures found. -->", note.getSolution(),
                    note.getAnswer(), note.getResult(), List.copyOf(note.getParts()));
        });

        String sql = new String(getClass().getResourceAsStream("/db/migration/V83__recap_missing_figures.sql")
                .readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(sql);

        assertThat(byKey(4).isFigureMissing()).isTrue();
        assertThat(byKey(1).isFigureMissing()).isFalse();
        assertThat(byKey(1).getStatement()).doesNotContain("<!--").isEqualTo("Un preț de 200 lei crește cu 10%.");
        assertThat(repository.findById(withNote).orElseThrow().isFigureMissing()).isFalse();
    }

    @Test
    void catchUpHomeworkGivesUnusedApprovedExercisesOfTheChapterToTheChosenStudents() throws Exception {
        upload(asAdmin()).andExpect(status().isOk());
        long percent = byKey(1).getId();
        approve(percent).andExpect(status().isOk());
        EnChapter a3 = enChapterRepository.findByCode("A3").orElseThrow();
        EnChapter a14 = enChapterRepository.findByCode("A14").orElseThrow();
        String due = Instant.now().plus(3, ChronoUnit.DAYS).toString();

        // no approved exercise on quadratic equations
        mockMvc.perform(post("/api/admin/recap/catch-up").with(asAdmin()).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("schoolClassId", schoolClass.getId(),
                                "enChapterId", a14.getId(), "studentIds", List.of(ana.getId()), "count", 3,
                                "dueAt", due))))
                .andExpect(status().isConflict());

        JsonNode given = json(mockMvc.perform(post("/api/admin/recap/catch-up").with(asAdmin()).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of(
                                "schoolClassId", schoolClass.getId(), "enChapterId", a3.getId(),
                                "studentIds", List.of(ana.getId()), "count", 1, "dueAt", due))))
                .andExpect(status().isCreated()));

        Quiz quiz = quizRepository.findById(given.get("quizId").asLong()).orElseThrow();
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(itemRepository.findByQuizIdOrderByPosition(quiz.getId())).hasSize(1);
        Assignment homework = assignmentRepository.findById(given.get("assignmentId").asLong()).orElseThrow();
        assertThat(homework.isCatchUp()).isTrue();
        List<Long> students = tx.execute(s -> List.copyOf(assignmentRepository.findById(homework.getId()).orElseThrow()
                .getStudentIds()));
        assertThat(students).containsExactly(ana.getId());
    }
}
