package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.enchapter.EnArea;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.gdpr.GdprErasureService;
import ro.mathlms.gdpr.GdprExportService;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.tutor.TutorClient;
import ro.mathlms.tutor.TutorException;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „De ce am greșit?" on real Postgres: a student asks why the option they chose is wrong; the teacher sees how many
 * asked next to that option, can draft the text from the lessons (tutor replaced here) and saves it with the lesson it
 * cites; from then on every student who chose that option reads it on their result, with a link to the lesson.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.tutor.enabled=true", "app.tutor.token=test-token",
        "app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class WhyWrongIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private EnChapterRepository chapterRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GdprErasureService erasureService;
    @Autowired private GdprExportService exportService;
    @Autowired private VerificationTokenService tokenService;
    @MockitoBean private TutorClient tutor;

    private User ana;
    private User ion;
    private User prof;
    private EnChapter chapter;
    private EnChapter section;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT));
        ion = userRepository.save(new User("ion." + stamp + "@scoala.ro", "Ion", Role.STUDENT));
        prof = userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.TEACHER));
        String base36 = "0000000" + Long.toString(System.nanoTime() / 100, 36).toUpperCase();
        String tail = base36.substring(base36.length() - 7);
        chapter = chapterRepository.save(new EnChapter("A" + tail, "Fracții", EnArea.ALGEBRA, 960, null));
        section = new EnChapter("S" + tail, "Adunarea fracțiilor", EnArea.ALGEBRA, 961, null);
        section.placeIn(chapter.getId(), 21);
        section = chapterRepository.save(section);
    }

    private RequestPostProcessor as(User student) {
        return user(student.getEmail()).authorities(STUDENT);
    }

    private RequestPostProcessor asProf() {
        return user(prof.getEmail()).roles("TEACHER");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Quiz test(int attempts) {
        Quiz q = new Quiz("Fracții " + System.nanoTime(), null);
        q.ownedBy(prof.getId());
        q.limitAttempts(attempts);
        q.publish();
        return quizRepository.save(q);
    }

    private QuizItem grila(Quiz quiz) {
        QuizItem item = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE,
                "Cât face $\\frac{1}{2} + \\frac{1}{3}$?", 5, null));
        optionRepository.save(new QuizOption(item, 0, "$\\frac{5}{6}$", true));
        optionRepository.save(new QuizOption(item, 1, "$\\frac{2}{5}$", false));
        return item;
    }

    private QuizOption option(QuizItem item, boolean correct) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == correct).findFirst().orElseThrow();
    }

    /** The student writes the test, choosing the right or the wrong option, and hands it in. */
    private Long handIn(User student, Quiz quiz, QuizItem item, boolean right) {
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.TEST);
        attemptService.saveResponse(attempt.attemptId(), item.getId(), option(item, right).getId(), student.getEmail());
        attemptService.submit(attempt.attemptId(), student.getEmail());
        return attempt.attemptId();
    }

    private ResultActions ask(Long attemptId, Long itemId, User student) throws Exception {
        return mockMvc.perform(post("/api/quiz/attempts/" + attemptId + "/items/" + itemId + "/why-wrong")
                .with(as(student)).with(csrf()));
    }

    private JsonNode resultItem(Long attemptId, User student) throws Exception {
        return json(mockMvc.perform(get("/api/quiz/attempts/" + attemptId + "/result").with(as(student)))
                .andExpect(status().isOk())).get("items").get(0);
    }

    private JsonNode optionStat(Quiz quiz, QuizOption option) throws Exception {
        for (JsonNode o : json(mockMvc.perform(get("/api/admin/quizzes/" + quiz.getId() + "/stats").with(asProf()))
                .andExpect(status().isOk())).get("items").get(0).get("options")) {
            if (o.get("id").asLong() == option.getId()) {
                return o;
            }
        }
        throw new AssertionError("option not in the stats");
    }

    private ResultActions save(QuizOption option, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/quiz-options/" + option.getId() + "/mistake").with(asProf()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aStudentAsksAndTheTeacherSeesHowManyAskedNextToTheOption() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        Long anaAttempt = handIn(ana, quiz, item, false);
        Long ionAttempt = handIn(ion, quiz, item, false);

        ask(anaAttempt, item.getId(), ana).andExpect(status().isNoContent());
        ask(anaAttempt, item.getId(), ana).andExpect(status().isNoContent()); // asking twice counts once
        ask(ionAttempt, item.getId(), ion).andExpect(status().isNoContent());

        assertThat(resultItem(anaAttempt, ana).get("explanationRequested").asBoolean()).isTrue();
        assertThat(optionStat(quiz, option(item, false)).get("requests").asInt()).isEqualTo(2);
        assertThat(optionStat(quiz, option(item, true)).get("requests").asInt()).isZero();
        JsonNode pending = json(mockMvc.perform(get("/api/admin/explanation-requests").with(asProf()))
                .andExpect(status().isOk()));
        assertThat(pending).anySatisfy(row -> {
            assertThat(row.get("quizId").asLong()).isEqualTo(quiz.getId());
            assertThat(row.get("students").asInt()).isEqualTo(2);
            assertThat(row.get("options").asInt()).isEqualTo(1);
        });
    }

    @Test
    void theSavedExplanationReachesEveryoneWhoChoseTheOptionWithItsLesson() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        Long anaAttempt = handIn(ana, quiz, item, false);
        Long ionAttempt = handIn(ion, quiz, item, false);
        ask(anaAttempt, item.getId(), ana).andExpect(status().isNoContent());

        save(option(item, false), "{\"mistake\": \"Ai adunat numitorii.\", \"lessonId\": " + section.getId()
                + ", \"lessonPage\": 21}").andExpect(status().isNoContent());

        for (JsonNode result : List.of(resultItem(anaAttempt, ana), resultItem(ionAttempt, ion))) {
            assertThat(result.get("mistake").asString()).isEqualTo("Ai adunat numitorii.");
            JsonNode lesson = result.get("mistakeLesson");
            assertThat(lesson.get("chapterId").asLong()).isEqualTo(chapter.getId());
            assertThat(lesson.get("sectionId").asLong()).isEqualTo(section.getId());
            assertThat(lesson.get("chapterCode").asString()).isEqualTo(chapter.getCode());
            assertThat(lesson.get("bookPage").asInt()).isEqualTo(21);
            assertThat(result.get("explanationRequested").asBoolean()).isFalse(); // answered: the request is gone
        }
        assertThat(optionStat(quiz, option(item, false)).get("requests").asInt()).isZero();
        assertThat(optionStat(quiz, option(item, false)).get("mistakeLesson").get("bookPage").asInt()).isEqualTo(21);
    }

    @Test
    void anExplanationWithoutALessonIsFineAndBlankTakesBothAway() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        Long attempt = handIn(ana, quiz, item, false);
        save(option(item, false), "{\"mistake\": \"Ai adunat numitorii.\", \"lessonId\": " + section.getId()
                + ", \"lessonPage\": 21}").andExpect(status().isNoContent());

        save(option(item, false), "{\"mistake\": \"\"}").andExpect(status().isNoContent());

        JsonNode result = resultItem(attempt, ana);
        assertThat(result.get("mistake").isNull()).isTrue();
        assertThat(result.get("mistakeLesson").isNull()).isTrue();
    }

    @Test
    void aLessonThatDoesNotExistIsRefused() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);

        save(option(item, false), "{\"mistake\": \"Ai adunat numitorii.\", \"lessonId\": 987654321, \"lessonPage\": 3}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAWrongChoiceWithNoExplanationYetCanBeAskedAbout() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        Long right = handIn(ana, quiz, item, true);
        Quiz explained = test(1);
        QuizItem explainedItem = grila(explained);
        QuizOption wrongOption = option(explainedItem, false);
        wrongOption.explainMistake("Deja scrisă.");
        optionRepository.save(wrongOption);
        Long wrong = handIn(ion, explained, explainedItem, false);

        ask(right, item.getId(), ana).andExpect(status().isConflict());
        ask(wrong, explainedItem.getId(), ion).andExpect(status().isConflict());
    }

    @Test
    void notWhileTheTestCanStillBeTakenAgain() throws Exception {
        Quiz quiz = test(3); // the answer key stays hidden while attempts are left
        QuizItem item = grila(quiz);
        Long attempt = handIn(ana, quiz, item, false);

        String message = ask(attempt, item.getId(), ana).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(message).contains("ultima încercare");
    }

    @Test
    void anotherStudentsAttemptIsNotFound() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        Long anaAttempt = handIn(ana, quiz, item, false);

        ask(anaAttempt, item.getId(), ion).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/quiz/attempts/" + anaAttempt + "/items/" + item.getId() + "/why-wrong")
                .with(asProf()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void theTeacherDraftsFromTheLessonsAndSeesTheLessonItCites() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        when(tutor.mistake(anyString(), any(), anyString(), any())).thenReturn(new TutorClient.MistakeDraft(true,
                "Ai adunat numitorii. Fracțiile se aduc la același numitor.",
                List.of(new TutorClient.Source(1, section.getCode(), "Adunarea fracțiilor", 21)), null, 800, 50, 0.0001));

        JsonNode body = json(mockMvc.perform(post("/api/admin/quiz-options/" + option(item, false).getId()
                + "/mistake-from-lessons").with(asProf()).with(csrf())).andExpect(status().isOk()));

        verify(tutor).mistake("Cât face $\\frac{1}{2} + \\frac{1}{3}$?\nVariante: a) $\\frac{5}{6}$; b) $\\frac{2}{5}$",
                null, "b) $\\frac{2}{5}$", "a) $\\frac{5}{6}$");
        assertThat(body.get("answered").asBoolean()).isTrue();
        assertThat(body.get("text").asString()).startsWith("Ai adunat numitorii.");
        assertThat(body.get("lesson").get("sectionId").asLong()).isEqualTo(section.getId());
        assertThat(body.get("lesson").get("chapterId").asLong()).isEqualTo(chapter.getId());
        assertThat(body.get("lesson").get("bookPage").asInt()).isEqualTo(21);
        // a draft is not a save
        assertThat(optionRepository.findById(option(item, false).getId()).orElseThrow().getMistake()).isNull();
    }

    @Test
    void aDraftTheLessonsDoNotCoverSaysSo() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        when(tutor.mistake(anyString(), any(), anyString(), any())).thenReturn(new TutorClient.MistakeDraft(false, "",
                List.of(), new TutorClient.Refusal("too_far", "Nu am asta în manual."), 0, 0, 0.0));

        JsonNode body = json(mockMvc.perform(post("/api/admin/quiz-options/" + option(item, false).getId()
                + "/mistake-from-lessons").with(asProf()).with(csrf())).andExpect(status().isOk()));

        assertThat(body.get("answered").asBoolean()).isFalse();
        assertThat(body.get("refusal").asString()).isEqualTo("Nu am asta în manual.");
        assertThat(body.get("lesson").isNull()).isTrue();
    }

    @Test
    void theRightOptionAndAnotherTeachersOptionGetNoDraft() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        User other = userRepository.save(new User("alt." + System.nanoTime() + "@scoala.ro", "Alt", Role.TEACHER));

        mockMvc.perform(post("/api/admin/quiz-options/" + option(item, true).getId() + "/mistake-from-lessons")
                .with(asProf()).with(csrf())).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/quiz-options/" + option(item, false).getId() + "/mistake-from-lessons")
                .with(user(other.getEmail()).roles("TEACHER")).with(csrf())).andExpect(status().isNotFound());

        verify(tutor, never()).mistake(anyString(), any(), anyString(), any());
    }

    @Test
    void whenTheTutorIsDownTheTeacherIsToldSo() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        when(tutor.mistake(anyString(), any(), anyString(), any())).thenThrow(new TutorException("Tutorul nu răspunde acum."));

        mockMvc.perform(post("/api/admin/quiz-options/" + option(item, false).getId() + "/mistake-from-lessons")
                .with(asProf()).with(csrf())).andExpect(status().isServiceUnavailable());
    }

    @Test
    void theStudentsRequestsAreInTheirDataExportAndGoWithTheAccount() throws Exception {
        Quiz quiz = test(1);
        QuizItem item = grila(quiz);
        Long attempt = handIn(ana, quiz, item, false);
        ask(attempt, item.getId(), ana).andExpect(status().isNoContent());

        String manifest = null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(exportService.exportZip(ana.getEmail())))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().equals("manifest.json")) {
                    manifest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        JsonNode requests = objectMapper.readTree(manifest).get("explanationRequests");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("chosenOption").asString()).isEqualTo("$\\frac{2}{5}$");

        erasureService.confirmErasure(tokenService.generate(ana.getEmail(), TokenPurpose.ERASE_ACCOUNT));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mistake_explanation_requests WHERE student_id = ?",
                Integer.class, ana.getId())).isZero();
    }
}
