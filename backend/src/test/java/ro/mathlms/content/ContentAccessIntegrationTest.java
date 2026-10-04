package ro.mathlms.content;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step "content access": a STUDENT reads the content tree (class → book → chapter → exercise) only
 * for the classes they are enrolled in; anything else answers 404 (a guessed id must not confirm
 * that it exists). Real Postgres + the full security chain; each test rolls back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentAccessIntegrationTest {

    private static final String ANA = "ana.acces@scoala.ro";
    private static final String MARIA = "maria.acces@scoala.ro";   // parent of Bob, who is enrolled in "mine"
    private static final String ION = "ion.acces@scoala.ro";       // parent without any linked child

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    /** The id chain of one class's content. */
    private record Tree(long classId, long bookId, long chapterId, long exerciseId) {
    }

    private Tree mine;   // Ana is enrolled here
    private Tree other;  // Ana is NOT enrolled here

    private Tree buildTree(String className) {
        SchoolClass schoolClass = classRepository.saveAndFlush(new SchoolClass(className, null));
        Book book = bookRepository.saveAndFlush(new Book(schoolClass, "Carte " + className, null));
        Chapter chapter = chapterRepository.saveAndFlush(new Chapter(book, "Capitol " + className, null));
        Exercise exercise = exerciseRepository.saveAndFlush(new Exercise(chapter, "Enunț " + className, null, null));
        return new Tree(schoolClass.getId(), book.getId(), chapter.getId(), exercise.getId());
    }

    @BeforeEach
    void seed() {
        User ana = userRepository.saveAndFlush(new User(ANA, "Ana Acces", Role.STUDENT));
        mine = buildTree("Clasa mea");
        other = buildTree("Clasa altuia");
        enrollmentRepository.saveAndFlush(
                new Enrollment(ana, classRepository.getReferenceById(mine.classId())));

        User maria = userRepository.saveAndFlush(new User(MARIA, "Maria Acces", Role.PARENT));
        userRepository.saveAndFlush(new User(ION, "Ion Acces", Role.PARENT));
        User bob = new User("bob.acces@scoala.ro", "Bob Acces", Role.STUDENT);
        bob.linkParent(maria);
        bob = userRepository.saveAndFlush(bob);
        enrollmentRepository.saveAndFlush(
                new Enrollment(bob, classRepository.getReferenceById(mine.classId())));
    }

    private void expectEveryReadOfTheTree(Tree tree, int expectedStatus) throws Exception {
        String[] urls = {
                "/api/classes/" + tree.classId(),
                "/api/classes/" + tree.classId() + "/books",
                "/api/books/" + tree.bookId(),
                "/api/books/" + tree.bookId() + "/chapters",
                "/api/chapters/" + tree.chapterId(),
                "/api/chapters/" + tree.chapterId() + "/exercises",
                "/api/exercises/" + tree.exerciseId(),
        };
        for (String url : urls) {
            mockMvc.perform(get(url)).andExpect(status().is(expectedStatus));
        }
    }

    @Test
    @WithMockUser(username = ANA, authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void aStudentReadsTheWholeTreeOfTheirOwnClass() throws Exception {
        expectEveryReadOfTheTree(mine, 200);
    }

    @Test
    @WithMockUser(username = ANA, authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void aStudentGetsNotFoundForEveryLevelOfAnotherClass() throws Exception {
        expectEveryReadOfTheTree(other, 404);
    }

    @Test
    @WithMockUser(username = ANA, authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void theClassListOfAStudentHoldsOnlyTheirClasses() throws Exception {
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Clasa mea")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Clasa altuia"))));
    }

    @Test
    @WithMockUser(username = "prof@scoala.ro", authorities = {"ROLE_ADMIN", "STATUS_ACTIVE"})
    void anAdminReadsEveryClass() throws Exception {
        expectEveryReadOfTheTree(mine, 200);
        expectEveryReadOfTheTree(other, 200);
        mockMvc.perform(get("/api/classes"))
                .andExpect(jsonPath("$[*].name", hasItem("Clasa mea")))
                .andExpect(jsonPath("$[*].name", hasItem("Clasa altuia")));
    }

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void aParentReadsTheTreeOfTheirChildrensClassOnly() throws Exception {
        expectEveryReadOfTheTree(mine, 200);
        expectEveryReadOfTheTree(other, 404);
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Clasa mea")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Clasa altuia"))));
    }

    @Test
    @WithMockUser(username = ION, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void aParentWithoutChildrenReadsNoClassAtAll() throws Exception {
        expectEveryReadOfTheTree(mine, 404);
        expectEveryReadOfTheTree(other, 404);
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
