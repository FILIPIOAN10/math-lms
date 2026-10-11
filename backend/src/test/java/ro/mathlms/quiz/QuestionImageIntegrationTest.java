package ro.mathlms.quiz;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pictures under question statements on real Postgres + the local file store: the teacher attaches, replaces and
 * removes them; the student who may take the quiz (or has taken it), and the parent of a student who has, may load
 * them; nobody else may - a 404, like for a question that does not exist.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuestionImageIntegrationTest {

    /** A real 1x1 PNG, so the stored file is an image. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;

    private Quiz quiz;
    private QuizItem item;
    private User ana;      // in the quiz's class
    private User strain;   // in another class, no attempt
    private User maria;    // Ana's parent
    private User altParinte;

    private RequestPostProcessor ADMIN; // a real teacher account: loading a picture checks who asks

    private static RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole()),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        SchoolClass clasa = schoolClassRepository.save(new SchoolClass("Clasa a 8-a " + stamp, null));
        SchoolClass alta = schoolClassRepository.save(new SchoolClass("Clasa a 7-a " + stamp, null));
        ADMIN = as(userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.ADMIN)));
        maria = userRepository.save(new User("maria." + stamp + "@scoala.ro", "Maria", Role.PARENT));
        altParinte = userRepository.save(new User("alt." + stamp + "@scoala.ro", "Alt", Role.PARENT));
        User a = new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT);
        a.linkParent(maria);
        ana = userRepository.save(a);
        strain = userRepository.save(new User("strain." + stamp + "@scoala.ro", "Străin", Role.STUDENT));
        enrollmentRepository.save(new Enrollment(ana, clasa));
        enrollmentRepository.save(new Enrollment(strain, alta));
        Quiz q = new Quiz("EN " + stamp, null);
        q.assignToClass(clasa);
        quiz = quizRepository.save(q);
        item = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "Din figură, AB =", 5, null));
    }

    private void upload(MockMultipartFile file, int expectedStatus) throws Exception {
        mockMvc.perform(multipart("/api/admin/quiz-items/" + item.getId() + "/image").file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(csrf()).with(ADMIN))
                .andExpect(status().is(expectedStatus));
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "figura.png", "image/png", PNG);
    }

    private String imageOf(Long quizId) throws Exception {
        String json = mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.items[0].image");
    }

    @Test
    void theTeacherAttachesAPictureThatTheClassSeesAndAStrangerDoesNot() throws Exception {
        upload(png(), 204);
        assertThat(imageOf(quiz.getId())).endsWith(".png");

        quiz.publish();
        quizRepository.save(quiz);

        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(as(strain)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDraftsPictureIsHiddenFromStudentsUntilTheyHaveAnAttempt() throws Exception {
        upload(png(), 204);

        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(as(ana))).andExpect(status().isNotFound());

        attemptRepository.save(new QuizAttempt(quiz, ana, Instant.now()));
        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(as(ana))).andExpect(status().isOk());
    }

    @Test
    void aParentSeesItOnlyThroughTheirChildsAttempt() throws Exception {
        upload(png(), 204);
        attemptRepository.save(new QuizAttempt(quiz, ana, Instant.now()));

        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(as(maria))).andExpect(status().isOk());
        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(as(altParinte)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyPicturesAreAccepted() throws Exception {
        upload(new MockMultipartFile("file", "nota.txt", "text/plain", "x".getBytes()), 400);
        assertThat(imageOf(quiz.getId())).isNull();
    }

    @Test
    void replacingGivesANewVersionAndRemovingHidesIt() throws Exception {
        upload(png(), 204);
        String first = imageOf(quiz.getId());
        upload(png(), 204);
        String second = imageOf(quiz.getId());
        assertThat(second).isNotEqualTo(first);

        mockMvc.perform(delete("/api/admin/quiz-items/" + item.getId() + "/image").with(csrf()).with(ADMIN))
                .andExpect(status().isNoContent());
        assertThat(imageOf(quiz.getId())).isNull();
        mockMvc.perform(get("/api/quiz/items/" + item.getId() + "/image").with(ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void aCopiedQuizGetsItsOwnCopyOfThePicture() throws Exception {
        upload(png(), 204);
        String original = imageOf(quiz.getId());

        String copy = mockMvc.perform(post("/api/admin/quizzes/" + quiz.getId() + "/copy").with(csrf()).with(ADMIN))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long copyId = ((Number) JsonPath.read(copy, "$.id")).longValue();

        String copied = imageOf(copyId);
        assertThat(copied).isNotNull().isNotEqualTo(original);
        String copyItem = mockMvc.perform(get("/api/admin/quizzes/" + copyId).with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        Long copyItemId = ((Number) JsonPath.read(copyItem, "$.items[0].id")).longValue();
        mockMvc.perform(get("/api/quiz/items/" + copyItemId + "/image").with(ADMIN))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));
    }

    @Test
    void aQuestionImportedWithImagineWaitsForItsPictureUntilOneIsAttached() throws Exception {
        String created = mockMvc.perform(post("/api/admin/quizzes/" + quiz.getId() + "/items").with(csrf()).with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OPEN\",\"position\":1,\"statement\":\"Calculează aria din figură\","
                                + "\"points\":5,\"imageRequired\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        item = itemRepository.findById(((Number) JsonPath.read(created, "$.id")).longValue()).orElseThrow();

        upload(png(), 204);

        mockMvc.perform(get("/api/admin/quizzes/" + quiz.getId()).with(ADMIN))
                .andExpect(jsonPath("$.items[1].imageRequired").value(false))
                .andExpect(jsonPath("$.items[1].image").isNotEmpty());
    }

    @Test
    void aStudentCannotAttachAPicture() throws Exception {
        mockMvc.perform(multipart("/api/admin/quiz-items/" + item.getId() + "/image").file(png())
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(csrf()).with(as(ana)))
                .andExpect(status().isForbidden());
    }
}
