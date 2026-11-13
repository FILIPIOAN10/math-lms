package ro.mathlms.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.enchapter.EnArea;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.review.ReviewDtos.ChapterPracticeDto;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Practising one EN chapter from its page, on real Postgres: only grile linked to the chapter, only from tests whose
 * answers the student may already see (handed in, or open for practice), never one of a test still running, the ones
 * answered wrong first - and an answer to a question outside that pool is refused.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class ChapterPracticeIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EnChapterRepository chapterRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private ChapterPracticeService service;

    private User ana;
    private EnChapter fractii;
    private EnChapter radicali;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        // Base 36 of nanoTime/100: on Windows nanoTime moves in 100 ns steps, so its last 5 digits gave ~1000 codes and
        // two setUps could collide on the unique chapter code. Left-padded: on a freshly booted CI runner nanoTime is
        // small and its base 36 can be shorter than 7 characters.
        String base36 = "0000000" + Long.toString(System.nanoTime() / 100, 36).toUpperCase();
        String tail = base36.substring(base36.length() - 7);
        fractii = chapterRepository.save(new EnChapter("F" + tail, "Fracții", EnArea.ALGEBRA, 900, null));
        radicali = chapterRepository.save(new EnChapter("R" + tail, "Radicali", EnArea.ALGEBRA, 901, null));
    }

    /** One attempt, as the test dialog sets by default: once handed in, its answers are the student's to see. */
    private Quiz test(String title, boolean practiceAllowed) {
        return test(title, practiceAllowed, 1);
    }

    private Quiz test(String title, boolean practiceAllowed, Integer maxAttempts) {
        Quiz q = new Quiz(title + " " + System.nanoTime(), null);
        q.allowPractice(practiceAllowed);
        q.limitAttempts(maxAttempts);
        q.publish();
        return quizRepository.save(q);
    }

    private QuizItem grila(Quiz quiz, int position, EnChapter chapter) {
        QuizItem item = new QuizItem(quiz, position, QuizItemType.SINGLE_CHOICE, "Întrebarea " + position, 5, null);
        item.pointToEnChapter(chapter);
        item = itemRepository.save(item);
        optionRepository.save(new QuizOption(item, 0, "bine", true));
        optionRepository.save(new QuizOption(item, 1, "greșit", false));
        return item;
    }

    private Long option(QuizItem item, boolean correct) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == correct).findFirst().orElseThrow().getId();
    }

    /** Ana writes the test and hands it in, answering each question right or wrong as asked. */
    private void handIn(Quiz quiz, QuizItem right, QuizItem wrong) {
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail(), AttemptMode.TEST);
        attemptService.saveResponse(attempt.attemptId(), right.getId(), option(right, true), ana.getEmail());
        attemptService.saveResponse(attempt.attemptId(), wrong.getId(), option(wrong, false), ana.getEmail());
        attemptService.submit(attempt.attemptId(), ana.getEmail());
    }

    private List<Long> ids(ChapterPracticeDto practice) {
        return practice.questions().stream().map(ReviewQuestionDto::itemId).toList();
    }

    @Test
    void aHandedInTestGivesItsChapterGrileWithTheWrongOneFirst() {
        Quiz quiz = test("Simulare", false);
        QuizItem right = grila(quiz, 1, fractii);
        QuizItem wrong = grila(quiz, 2, fractii);
        QuizItem other = grila(quiz, 3, radicali);
        handIn(quiz, right, wrong);

        ChapterPracticeDto practice = service.practice(ana.getEmail(), fractii.getId());

        assertThat(practice.code()).isEqualTo(fractii.getCode());
        assertThat(practice.available()).isEqualTo(2);
        assertThat(ids(practice)).containsExactly(wrong.getId(), right.getId()).doesNotContain(other.getId());
    }

    @Test
    void aTestNotYetWrittenOrStillRunningGivesNothingAway() {
        Quiz notYet = test("Tema de mâine", false);
        grila(notYet, 1, fractii);
        Quiz running = test("Test început", true); // even a practice-friendly test, while the student writes it
        grila(running, 1, fractii);
        attemptService.startAttempt(running.getId(), ana.getEmail(), AttemptMode.TEST);

        ChapterPracticeDto practice = service.practice(ana.getEmail(), fractii.getId());

        assertThat(practice.available()).isZero();
        assertThat(practice.questions()).isEmpty();
    }

    @Test
    void aHandedInTestWithARetakeLeftStaysOutUntilTheLastAttempt() {
        Quiz twice = test("Două încercări", false, 2);
        QuizItem right = grila(twice, 1, fractii);
        QuizItem wrong = grila(twice, 2, fractii);
        handIn(twice, right, wrong);

        assertThat(service.practice(ana.getEmail(), fractii.getId()).available()).isZero();

        handIn(twice, right, wrong); // the second and last attempt: now the answers are hers to see

        assertThat(ids(service.practice(ana.getEmail(), fractii.getId()))).containsExactly(wrong.getId(), right.getId());
    }

    @Test
    void aTestOpenForPracticeCountsWithoutBeingWritten() {
        Quiz exersare = test("Exersare fracții", true);
        QuizItem item = grila(exersare, 1, fractii);

        assertThat(ids(service.practice(ana.getEmail(), fractii.getId()))).containsExactly(item.getId());
    }

    @Test
    void checksAnAnswerAtOnceAndRefusesAQuestionOutsideThePractice() throws Exception {
        Quiz exersare = test("Exersare fracții", true);
        QuizItem item = grila(exersare, 1, fractii);
        QuizItem elsewhere = grila(exersare, 2, radicali);

        mockMvc.perform(get("/api/quiz/en-chapters/" + fractii.getId() + "/practice")
                        .with(user(ana.getEmail()).authorities(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].itemId").value(item.getId()))
                .andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist()); // the key stays on the server

        mockMvc.perform(post("/api/quiz/en-chapters/" + fractii.getId() + "/practice/items/" + item.getId() + "/answer")
                        .with(user(ana.getEmail()).authorities(STUDENT)).with(csrf())
                        .contentType("application/json").content("{\"optionId\":" + option(item, false) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correctOptionId").value(option(item, true)));

        mockMvc.perform(post("/api/quiz/en-chapters/" + fractii.getId() + "/practice/items/" + elsewhere.getId() + "/answer")
                        .with(user(ana.getEmail()).authorities(STUDENT)).with(csrf())
                        .contentType("application/json").content("{\"optionId\":" + option(elsewhere, true) + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/quiz/en-chapters/999999999/practice").with(user(ana.getEmail()).authorities(STUDENT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAStudentPractises() throws Exception {
        mockMvc.perform(get("/api/quiz/en-chapters/" + fractii.getId() + "/practice")
                        .with(user("prof@scoala.ro").authorities(new SimpleGrantedAuthority("ROLE_TEACHER"),
                                new SimpleGrantedAuthority("STATUS_ACTIVE"))))
                .andExpect(status().isForbidden());
    }
}
