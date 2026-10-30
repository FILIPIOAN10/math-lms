package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.TestImages;
import ro.mathlms.assignment.Assignment;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class QuizRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(TestImages.POSTGRES);

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private QuizItemRepository quizItemRepository;

    @Autowired
    private QuizOptionRepository quizOptionRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findsPublishedQuizzes() {
        Quiz draft = new Quiz("Draft", null);
        Quiz published = new Quiz("Publicat", null);
        published.publish();
        quizRepository.save(draft);
        quizRepository.save(published);

        List<Quiz> result = quizRepository.findByStatusOrderByTitle(QuizStatus.PUBLISHED);

        assertThat(result).extracting(Quiz::getTitle).containsExactly("Publicat");
    }

    private Quiz publishedQuiz(String title, SchoolClass schoolClass) {
        Quiz quiz = new Quiz(title, null);
        quiz.assignToClass(schoolClass);
        quiz.publish();
        return quizRepository.save(quiz);
    }

    @Test
    void quizzesForEveryoneAreThoseWithoutAClass() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        publishedQuiz("Pentru toți", null);
        publishedQuiz("Doar clasa a 9-a", ninth);

        List<Quiz> result = quizRepository.findByStatusAndSchoolClassIsNullOrderByTitle(QuizStatus.PUBLISHED);

        assertThat(result).extracting(Quiz::getTitle).containsExactly("Pentru toți");
    }

    @Test
    void visibleToClassesIncludesQuizzesForEveryoneAndForThoseClassesOnly() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        SchoolClass tenth = schoolClassRepository.save(new SchoolClass("Clasa a 10-a", null));
        publishedQuiz("C - pentru toți", null);
        publishedQuiz("A - clasa a 9-a", ninth);
        publishedQuiz("B - clasa a 10-a", tenth);
        Quiz draftForNinth = new Quiz("D - ciornă a 9-a", null);
        draftForNinth.assignToClass(ninth);
        quizRepository.save(draftForNinth);

        List<Quiz> result = quizRepository.findVisibleToClasses(QuizStatus.PUBLISHED, List.of(ninth.getId()));

        assertThat(result).extracting(Quiz::getTitle).containsExactly("A - clasa a 9-a", "C - pentru toți");
    }

    @Test
    void savesItemsAndOptionsInOrder() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem s2 = quizItemRepository.save(
                new QuizItem(quiz, 2, QuizItemType.SINGLE_CHOICE, "s2", 5, null));
        quizItemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s1", 5, null));
        quizOptionRepository.save(new QuizOption(s2, 1, "B", false));
        quizOptionRepository.save(new QuizOption(s2, 0, "A", true));

        List<QuizItem> items = quizItemRepository.findByQuizIdOrderByPosition(quiz.getId());
        List<QuizOption> options = quizOptionRepository.findByItemIdOrderByPosition(s2.getId());

        assertThat(items).extracting(QuizItem::getStatement).containsExactly("s1", "s2");
        assertThat(options).extracting(QuizOption::getText).containsExactly("A", "B");
        assertThat(options).filteredOn(QuizOption::isCorrect).extracting(QuizOption::getText)
                .containsExactly("A");
    }

    @Test
    void deleteByItemIdRemovesAnItemsOptions() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem item = quizItemRepository.save(
                new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s1", 5, null));
        quizOptionRepository.save(new QuizOption(item, 0, "A", true));
        quizOptionRepository.save(new QuizOption(item, 1, "B", false));

        quizOptionRepository.deleteByItemId(item.getId());

        assertThat(quizOptionRepository.findByItemIdOrderByPosition(item.getId())).isEmpty();
    }

    @Test
    void listSummariesCountQuestionsAndPapersAverageGradedTestsAndPickTheNextDeadline() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        SchoolClass tenth = schoolClassRepository.save(new SchoolClass("Clasa a 10-a", null));
        SchoolClass eleventh = schoolClassRepository.save(new SchoolClass("Clasa a 11-a", null));
        Quiz quiz = quizRepository.save(new Quiz("Teza", null));
        Quiz plain = quizRepository.save(new Quiz("Fără lucrări", null));
        Quiz past = quizRepository.save(new Quiz("Temă veche", null));
        em.persist(new QuizItem(quiz, 0, QuizItemType.OPEN, "Rezolvă", 6, null));
        em.persist(new QuizItem(quiz, 1, QuizItemType.OPEN, "Arată", 4, null));
        User ana = em.persist(new User("ana@scoala.ro", "Ana", Role.STUDENT));
        User radu = em.persist(new User("radu@scoala.ro", "Radu", Role.STUDENT));
        User ion = em.persist(new User("ion@scoala.ro", "Ion", Role.STUDENT));
        // one attempt in progress per student and test: each is graded before the next one starts
        grade(em.persist(new QuizAttempt(quiz, ana)), "GRADED", 10);  // nota 10
        grade(em.persist(new QuizAttempt(quiz, radu)), "GRADED", 5);  // nota 5,50
        grade(em.persist(new QuizAttempt(quiz, ion)), "SUBMITTED", null); // a paper, not a grade yet
        grade(em.persist(new QuizAttempt(quiz, ana, Instant.now(), AttemptMode.PRACTICE)), "GRADED", 0); // neither
        em.persist(new QuizAttempt(quiz, radu)); // still in progress: not handed in
        Instant now = Instant.now();
        Instant soon = now.plus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant yesterday = now.minus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        em.persist(new Assignment(quiz, ninth, yesterday, now.minus(Duration.ofDays(5))));
        em.persist(new Assignment(quiz, tenth, soon, now));
        em.persist(new Assignment(quiz, eleventh, soon.plus(Duration.ofDays(3)), now));
        em.persist(new Assignment(past, ninth, yesterday, now.minus(Duration.ofDays(5))));
        em.flush();

        var rows = quizRepository.listSummaries();

        assertThat(rows).filteredOn(r -> r.getQuizId().equals(quiz.getId())).singleElement().satisfies(r -> {
            assertThat(r.getQuestions()).isEqualTo(2);
            assertThat(r.getPapers()).isEqualTo(3);
            assertThat(r.getAverage()).isEqualTo(7.75);
            assertThat(r.getDueAt()).isEqualTo(soon);
        });
        assertThat(rows).filteredOn(r -> r.getQuizId().equals(plain.getId())).singleElement().satisfies(r -> {
            assertThat(r.getQuestions()).isZero();
            assertThat(r.getPapers()).isZero();
            assertThat(r.getAverage()).isNull();
            assertThat(r.getDueAt()).isNull();
        });
        assertThat(rows).filteredOn(r -> r.getQuizId().equals(past.getId())).singleElement()
                .satisfies(r -> assertThat(r.getDueAt()).isEqualTo(yesterday));
    }

    private void grade(QuizAttempt attempt, String status, Integer score) {
        em.flush();
        em.getEntityManager()
                .createNativeQuery("update quiz_attempts set status = :status, score = cast(:score as int) where id = :id")
                .setParameter("status", status).setParameter("score", score).setParameter("id", attempt.getId())
                .executeUpdate();
    }
}
