package ro.mathlms.mastery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import ro.mathlms.review.Confidence;
import ro.mathlms.review.ReviewAnswer;
import ro.mathlms.review.ReviewAnswerRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „La ce stau prost?" on real Postgres: the student's weakest EN chapters from their own answers - the latest answer to
 * each question for the level, recent answers and "Știu sigur" mistakes for the reasons - with where to read and
 * practise each one. Computed from data only: no model is asked anything.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class WeakSpotsIntegrationTest {

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
    @Autowired private ReviewAnswerRepository reviewAnswerRepository;

    private User ana;
    private EnChapter fractii;
    private EnChapter radicali;
    private EnChapter puteri;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("ana." + System.nanoTime() + "@scoala.ro", "Ana", Role.STUDENT));
        String base36 = "0000000" + Long.toString(System.nanoTime() / 100, 36).toUpperCase();
        String tail = base36.substring(base36.length() - 7);
        fractii = chapterRepository.save(new EnChapter("F" + tail, "Fracții", EnArea.ALGEBRA, 980, null));
        radicali = chapterRepository.save(new EnChapter("R" + tail, "Radicali", EnArea.ALGEBRA, 981, null));
        puteri = new EnChapter("P" + tail, "Puteri", EnArea.ALGEBRA, 982, null);
        puteri.placeIn(radicali.getId(), 40); // a section of Radicali
        puteri = chapterRepository.save(puteri);
    }

    private RequestPostProcessor asAna() {
        return user(ana.getEmail()).authorities(STUDENT);
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

    /** Ana writes the test and hands it in, each question right or wrong as given. */
    private void handIn(Map<QuizItem, Boolean> answers) {
        Quiz quiz = answers.keySet().iterator().next().getQuiz();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail(), AttemptMode.TEST);
        answers.forEach((item, right) ->
                attemptService.saveResponse(attempt.attemptId(), item.getId(), option(item, right), ana.getEmail()));
        attemptService.submit(attempt.attemptId(), ana.getEmail());
    }

    private Quiz test() {
        Quiz q = new Quiz("Simulare " + System.nanoTime(), null);
        q.limitAttempts(1);
        q.publish();
        return quizRepository.save(q);
    }

    private JsonNode weakSpots() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/quiz/weak-spots").with(asAna()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void theWeakestChaptersComeWithTheirNumbersAndWhereToReadAndPractise() throws Exception {
        Quiz quiz = test();
        QuizItem f1 = grila(quiz, 0, fractii);
        QuizItem f2 = grila(quiz, 1, fractii);
        QuizItem p1 = grila(quiz, 2, puteri);
        QuizItem p2 = grila(quiz, 3, puteri);
        QuizItem p3 = grila(quiz, 4, puteri);
        QuizItem r1 = grila(quiz, 5, radicali);
        QuizItem r2 = grila(quiz, 6, radicali);
        handIn(Map.of(f1, false, f2, true, p1, false, p2, false, p3, true, r1, true, r2, true));
        reviewAnswerRepository.save(new ReviewAnswer(ana, p1, optionRepository.findById(option(p1, false)).orElseThrow(),
                false, Instant.now(), Confidence.SURE));

        JsonNode spots = weakSpots();

        assertThat(spots).hasSize(2); // Radicali at 100% is not weak
        JsonNode first = spots.get(0);
        assertThat(first.get("chapterId").asLong()).isEqualTo(puteri.getId());
        assertThat(first.get("title").asString()).isEqualTo("Puteri");
        assertThat(first.get("percent").asInt()).isEqualTo(33);
        assertThat(first.get("questions").asInt()).isEqualTo(3);
        assertThat(first.get("recentWrong").asInt()).isEqualTo(3); // two in the test, one in the review
        assertThat(first.get("recentAnswered").asInt()).isEqualTo(4);
        assertThat(first.get("sureWrong").asInt()).isEqualTo(1);
        assertThat(first.get("lessonChapterId").asLong()).isEqualTo(radicali.getId()); // a section opens its chapter
        assertThat(first.get("lessonSectionId").asLong()).isEqualTo(puteri.getId());
        assertThat(spots.get(1).get("chapterId").asLong()).isEqualTo(fractii.getId());
        assertThat(spots.get(1).get("percent").asInt()).isEqualTo(50);
        assertThat(spots.get(1).get("lessonSectionId").isNull()).isTrue();
    }

    @Test
    void aStudentWithNoAnswersHasNothingToShow() throws Exception {
        assertThat(weakSpots()).isEmpty();
    }

    @Test
    void onlyTheStudentAsksAboutThemselves() throws Exception {
        mockMvc.perform(get("/api/quiz/weak-spots").with(user("prof@scoala.ro").roles("TEACHER")))
                .andExpect(status().isForbidden());
    }
}
