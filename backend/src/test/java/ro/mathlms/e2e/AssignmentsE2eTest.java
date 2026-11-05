package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chapter 6 of the manual test guide, homework (Teme): homework starts the attempts afresh, the teacher's Acasă says
 * who is missing and opens the hand-in list, a paper handed in after the deadline is "cu întârziere" for the teacher
 * and "Predată cu întârziere" for the parent, and the Teme filters and "Șterge tema". New students and teachers come
 * from Mailpit; their classes are "E2E invitatie …" and the tests "E2E teme …", which deploy/dev-cleanup-e2e.sql removes.
 * <p>
 * The server takes only a future deadline (also when it is moved), so an overdue homework is given a deadline a few
 * seconds away and the test waits for it to pass.
 */
class AssignmentsE2eTest extends E2eBase {

    private static final String STUDENT_PASSWORD = "TestTeme1!";
    private static final String TEACHER_PASSWORD = "TestProf6!";
    private static final By ROWS = By.cssSelector("[data-testid='assignment-row']");

    private final ApiSeeder admin = new ApiSeeder(API_URL);

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
        admin.login(ADMIN, PASSWORD);
    }

    private static String quizTitle() {
        return "E2E teme " + System.nanoTime();
    }

    /** A published one-grilă test for this class ("patru" is right). */
    private long grilaQuiz(String title, long classId, Integer maxAttempts) {
        long quizId = admin.createQuiz(title, classId, null, false, maxAttempts);
        admin.addChoice(quizId, 0, "Cât face $2+2$?", "patru", "cinci", null);
        admin.publish(quizId);
        return quizId;
    }

    /** One more student in the class, signed up with its own invite link. */
    private void anotherStudent(long classId, String fullName, String email) {
        new ApiSeeder(API_URL).signUp(admin.classInvite(classId, 1), fullName, email, STUDENT_PASSWORD);
        new ApiSeeder(API_URL).confirmEmail(Mailbox.linkTo(email, "/verify-email"));
    }

    /** A new teacher (invite, sign-up, confirmation, approval) who is given this class. */
    private String teacherOf(long classId) {
        String email = freshEmail("prof");
        new ApiSeeder(API_URL).signUp(admin.teacherInvite(), "Profesor Teme", email, TEACHER_PASSWORD);
        new ApiSeeder(API_URL).confirmEmail(Mailbox.linkTo(email, "/verify-email"));
        admin.approve(email, "TEACHER");
        admin.giveClassTo(classId, email);
        return email;
    }

    private ApiSeeder as(String email, String password) {
        ApiSeeder client = new ApiSeeder(API_URL);
        client.login(email, password);
        return client;
    }

    /** A deadline a few seconds away, to wait out with {@link #waitPast}. */
    private static Instant inAFewSeconds() {
        return Instant.now().plusSeconds(4);
    }

    private static void waitPast(Instant due) throws InterruptedException {
        long ms = due.toEpochMilli() - System.currentTimeMillis() + 1500;
        if (ms > 0) {
            Thread.sleep(ms);
        }
    }

    private WebElement row(String title) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='assignment-row'][.//p[contains(normalize-space(.), '" + title + "')]]")));
    }

    @Test
    void homeworkStartsTheAttemptsAgainEvenWithAnOldPaperWaitingForItsGrade() {
        String email = freshEmail("tema");
        long classId = newStudent("Elev Tema", email, STUDENT_PASSWORD);
        long quizId = admin.createQuiz(quizTitle(), classId, null, false, 1);
        admin.addChoice(quizId, 0, "Cât face $2+2$?", "patru", "cinci", null);
        admin.addOpen(quizId, 1, "Rezolvă ecuația $2x = 6$.", 5);
        admin.publish(quizId);
        ApiSeeder student = as(email, STUDENT_PASSWORD);
        student.takeAndSubmit(quizId, "patru"); // handed in before the homework: waits for the teacher

        login(email, STUDENT_PASSWORD);
        open("/quizzes/" + quizId + "/take");
        assertThat(byTestId("quiz-blocked").getText()).startsWith("Lucrarea ta se corectează.");

        admin.assign(quizId, classId, Instant.now().plus(2, ChronoUnit.DAYS));
        driver.navigate().refresh();
        assertThat(byTestId("quiz-homework-due").getText()).startsWith("Temă — termen:");
        assertThat(byTestId("quiz-attempt-rule").getText()).isEqualTo("Ai o singură încercare.");
        assertThat(driver.findElements(By.cssSelector("[data-testid='quiz-blocked']"))).isEmpty();

        TakeQuizPage take = new TakeQuizPage(driver, wait);
        take.start();
        take.choose("patru");
        take.submit();
        assertThat(student.myAttempts(quizId)).as("the old paper and the homework").hasSize(2);
    }

    @Test
    void theTeachersAcasaSaysWhoIsMissingAndOpensTheHandInList() {
        long stamp = System.nanoTime();
        String done = "Elev Predat " + stamp;
        String missing = "Elev Lipsa " + stamp;
        String email = freshEmail("predat");
        long classId = newStudent(done, email, STUDENT_PASSWORD);
        anotherStudent(classId, missing, freshEmail("lipsa"));
        String teacher = teacherOf(classId);
        String title = quizTitle();
        long quizId = grilaQuiz(title, classId, null);
        long homework = admin.assign(quizId, classId, Instant.now().plus(1, ChronoUnit.DAYS));
        as(email, STUDENT_PASSWORD).takeAndSubmit(quizId, "patru");

        login(teacher, TEACHER_PASSWORD);
        WebElement deadline = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='deadline-row'][.//*[@data-testid='deadline-title' and normalize-space()='" + title + "']]")));
        wait.until(ExpectedConditions.textToBePresentInElement(deadline, "Lipsește: " + missing));
        assertThat(deadline.getText()).contains("1 / 2 predate").doesNotContain(done);

        deadline.click();
        wait.until(ExpectedConditions.urlContains("/admin/assignments?open=" + homework));
        WebElement list = byTestId("assignment-status");
        assertThat(list.getText()).contains(done).contains(missing);
        assertThat(row(title).findElement(By.cssSelector("[data-testid='assignment-counts']")).getText())
                .contains("Predate 1/2").contains("neîncepute 1");
    }

    @Test
    void aPaperAfterTheDeadlineIsLateForTheTeacherAndTheParent() throws InterruptedException {
        String email = freshEmail("intarziat");
        long classId = newStudent("Elev Intarziat", email, STUDENT_PASSWORD);
        admin.linkParent(email, PARENT);
        String title = quizTitle();
        long quizId = grilaQuiz(title, classId, null);
        Instant due = inAFewSeconds();
        long homework = admin.assign(quizId, classId, due);

        // A deadline cannot be moved into the past: it is the student who hands in late
        assertThatThrownBy(() -> admin.reschedule(homework, Instant.now().minus(1, ChronoUnit.DAYS)))
                .hasMessageContaining("Termenul trebuie să fie în viitor");
        waitPast(due);
        as(email, STUDENT_PASSWORD).takeAndSubmit(quizId, "patru");

        login(ADMIN, PASSWORD);
        open("/admin/assignments?open=" + homework);
        WebElement row = row(title);
        assertThat(row.getText()).contains("termen depășit");
        assertThat(row.findElement(By.cssSelector("[data-testid='assignment-counts']")).getText())
                .contains("Predate 1/1").contains("cu întârziere 1");
        assertThat(row.findElement(By.cssSelector("[data-testid='assignment-status']")).getText()).contains("(cu întârziere)");
        logout();

        login(PARENT, PASSWORD);
        open("/parent/children/" + admin.getUserIdForTest("STUDENT", email));
        WebElement card = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='assignment-card'][contains(normalize-space(.), '" + title + "')]")));
        assertThat(card.getText()).contains("Predată cu întârziere");
    }

    @Test
    void temeFiltersOpenAndOverdueHomeworkAndDeletesOneFromItsMenu() throws InterruptedException {
        long classId = newStudent("Elev Filtre", freshEmail("filtre"), STUDENT_PASSWORD);
        String teacher = teacherOf(classId);
        List<String> open = new ArrayList<>();
        List<String> overdue = new ArrayList<>();
        Instant due = inAFewSeconds();
        for (int i = 0; i < 5; i++) {
            String title = quizTitle();
            long quizId = grilaQuiz(title, classId, null);
            if (i < 3) {
                open.add(title);
                admin.assign(quizId, classId, Instant.now().plus(3, ChronoUnit.DAYS));
            } else {
                overdue.add(title);
                admin.assign(quizId, classId, due);
            }
        }
        waitPast(due);

        // The teacher sees only the homework of their own class: exactly these 5, enough for the filters to show
        login(teacher, TEACHER_PASSWORD);
        open("/admin/assignments");
        wait.until(ExpectedConditions.numberOfElementsToBe(ROWS, 5));
        Select term = new Select(wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("select[aria-label='Termen']"))));

        term.selectByVisibleText("Deschise");
        wait.until(ExpectedConditions.numberOfElementsToBe(ROWS, 3));
        assertThat(driver.findElements(ROWS)).allSatisfy(r -> assertThat(r.getText()).doesNotContain("termen depășit"));
        open.forEach(this::row);

        term.selectByVisibleText("Cu termen depășit");
        wait.until(ExpectedConditions.numberOfElementsToBe(ROWS, 2));
        assertThat(driver.findElements(ROWS)).allSatisfy(r -> assertThat(r.getText()).contains("termen depășit"));
        overdue.forEach(this::row);

        term.selectByVisibleText("Toate");
        wait.until(ExpectedConditions.numberOfElementsToBe(ROWS, 5));

        String gone = open.get(0);
        chooseFromMenu(gone, "Șterge tema");
        WebElement confirm = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role='alertdialog'], [role='dialog']")));
        wait.until(ExpectedConditions.textToBePresentInElement(confirm, "Ștergi tema „" + gone + "”?"));
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        wait.until(ExpectedConditions.numberOfElementsToBe(ROWS, 4));
        assertThat(driver.findElements(By.xpath(
                "//*[@data-testid='assignment-row'][.//p[contains(normalize-space(.), '" + gone + "')]]"))).isEmpty();
    }
}
