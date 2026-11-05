package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 5 of the manual test guide, the student takes the test (/quizzes/:id/take): the resume screen, the attempts
 * rule, numeric answers, the photo limit, a practice with its verdict, mistake, hints and EN lesson, the clock that
 * hands the test in by itself, and the "Urmează" homework card on Acasă. Each test has its own new student (Mailpit),
 * so nothing another test left on student.activ gets in the way; their classes are "E2E invitatie …", their tests
 * "E2E elev …" and the EN lesson "E2E<digits>", all removed by deploy/dev-cleanup-e2e.sql.
 */
class StudentTakesTestE2eTest extends E2eBase {

    private static final String STUDENT_PASSWORD = "TestElev1!";

    private final ApiSeeder admin = new ApiSeeder(API_URL);
    private String email;
    private long classId;

    @BeforeEach
    void newStudentInHisOwnClass() {
        assumeMailpit();
        email = freshEmail("elev");
        classId = newStudent("Elev Test", email, STUDENT_PASSWORD); // enrolled in that class by its invite link
        admin.login(ADMIN, PASSWORD);
    }

    private static String quizTitle() {
        return "E2E elev " + System.nanoTime();
    }

    private ApiSeeder asStudent() {
        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(email, STUDENT_PASSWORD);
        return student;
    }

    private TakeQuizPage take() {
        return new TakeQuizPage(driver, wait);
    }

    /** A grilă as the editor sends it: the variants in order, {@code right} = the index of the correct one. */
    private static Map<String, Object> grila(int position, String statement, int points, int right, String... variants) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < variants.length; i++) {
            options.add(new HashMap<>(Map.of("position", i, "text", variants[i], "correct", i == right)));
        }
        Map<String, Object> item = new HashMap<>();
        item.put("type", "SINGLE_CHOICE");
        item.put("position", position);
        item.put("statement", statement);
        item.put("points", points);
        item.put("options", options);
        return item;
    }

    @Test
    void theResumeScreenSaysWhatWasAnsweredAndOffersContinuaTestul() {
        String title = quizTitle();
        long quizId = admin.createQuiz(title, classId, null, false, null);
        admin.addItem(quizId, grila(0, "Cât face $1+1$?", 5, 0, "doi", "trei"));
        admin.addItem(quizId, grila(1, "Cât face $2+3$?", 5, 0, "cinci", "șase"));
        admin.publish(quizId);

        login(email, STUDENT_PASSWORD);
        open("/quizzes/" + quizId + "/take");
        assertThat(byTestId("quiz-start").getText()).isEqualTo("Începe testul");
        assertThat(driver.findElements(By.cssSelector("[data-testid='quiz-resume']"))).isEmpty();
        take().start();
        take().choose("doi");
        byTestId("quiz-leave").click(); // no clock: it leaves at once, once the answer is on the server
        assertThat(byTestId("saved-notice").getText()).contains(title);

        new StudentQuizzesPage(driver, wait).openQuiz(title);
        assertThat(byTestId("quiz-resume").getText()).isEqualTo("Ai început deja testul: ai răspuns la 1 din 2 întrebări.");
        assertThat(byTestId("quiz-start").getText()).isEqualTo("Continuă testul");
        take().start();
        WebElement kept = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//label[contains(normalize-space(.), 'doi')]//input[@type='radio']")));
        wait.until(ExpectedConditions.elementSelectionStateToBe(kept, true));
        assertThat(byTestId("quiz-answered").getText()).startsWith("1 / 2");
        assertThat(asStudent().myAttempts(quizId)).as("the same attempt, resumed").hasSize(1);
    }

    @Test
    void threeAttemptsWaitForTheGradeThenRunOutAndTheHighestGradeCounts() {
        String title = quizTitle();
        long quizId = admin.createQuiz(title, classId, null, false, 3);
        admin.addItem(quizId, grila(0, "Cât face $2+2$?", 5, 0, "patru", "cinci"));
        long open = admin.addOpen(quizId, 1, "Rezolvă ecuația $2x = 6$.", 5);
        admin.publish(quizId);
        ApiSeeder student = asStudent();

        login(email, STUDENT_PASSWORD);
        open("/quizzes/" + quizId + "/take");
        assertThat(byTestId("quiz-attempt-rule").getText()).isEqualTo("Încercarea 1 din 3. Contează cea mai mare notă.");

        // 1st: the grilă wrong, the open answer waits for the teacher - no new attempt until it is graded
        long first = student.takeAndSubmit(quizId, "cinci");
        driver.navigate().refresh();
        assertThat(byTestId("quiz-blocked").getText())
                .isEqualTo("Lucrarea ta se corectează. Poți da testul din nou după ce primești nota.");
        assertThat(driver.findElements(By.cssSelector("[data-testid='quiz-start']"))).isEmpty();
        admin.gradeAndFinalize(first, open, 2); // 2 / 10

        driver.navigate().refresh();
        assertThat(byTestId("quiz-attempt-rule").getText()).isEqualTo("Încercarea 2 din 3. Contează cea mai mare notă.");
        admin.gradeAndFinalize(student.takeAndSubmit(quizId, "patru"), open, 5); // 10 / 10
        admin.gradeAndFinalize(student.takeAndSubmit(quizId, "patru"), open, 0); // 5 / 10

        driver.navigate().refresh();
        assertThat(byTestId("quiz-blocked").getText()).isEqualTo("Ai folosit toate încercările la acest test.");
        assertThat(driver.findElements(By.cssSelector("[data-testid='quiz-start']"))).isEmpty();

        open("/quizzes");
        WebElement card = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='quiz-card'][contains(normalize-space(.), '" + title + "')]")));
        assertThat(card.findElement(By.cssSelector("[data-testid='quiz-attempts']")).getText()).isEqualTo("3 din 3 încercări");
        assertThat(card.findElement(By.cssSelector("[data-testid='quiz-grade']")).getText())
                .contains("nota 10").contains("cea mai mare din 3"); // the 10 of the second, not the last 5,5
        assertThat(card.findElements(By.xpath(".//*[@data-testid='quiz-open']"))).as("no \"Dă din nou\"").isEmpty();
    }

    @Test
    void aNumberWrittenAs0Comma75OrAsAFractionIsMarkedRight() {
        long quizId = admin.createQuiz(quizTitle(), classId, null, false, null);
        for (int i = 0; i < 2; i++) {
            admin.addItem(quizId, new HashMap<>(Map.of("type", "NUMERIC", "position", i, "points", 5, "numericAnswer", "3/4",
                    "statement", "Cât face $\\frac{1}{2}+\\frac{1}{4}$? (" + (i + 1) + ")")));
        }
        admin.publish(quizId);

        login(email, STUDENT_PASSWORD);
        open("/quizzes/" + quizId + "/take");
        take().start();
        List<WebElement> boxes = wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid='item-number']"), 2));
        boxes.get(0).sendKeys("0,75");
        boxes.get(1).sendKeys("3/4");
        take().submit();

        JsonNode attempt = asStudent().myAttempts(quizId).get(0);
        assertThat(attempt.get("status").asString()).isEqualTo("GRADED");
        assertThat(attempt.get("score").asInt()).as("both forms are 3/4").isEqualTo(10);
    }

    @Test
    void fivePhotosAtMostAndOnlyImages() throws Exception {
        long quizId = admin.createQuiz(quizTitle(), classId, null, false, null);
        long open = admin.addOpen(quizId, 0, "Rezolvă pe hârtie și fotografiază rezolvarea.", 10);
        admin.publish(quizId);
        Path photo = samplePng();
        Path notAnImage = Files.createTempFile("e2e", ".txt");
        Files.writeString(notAnImage, "nu e o poză");
        notAnImage.toFile().deleteOnExit();

        login(email, STUDENT_PASSWORD);
        open("/quizzes/" + quizId + "/take");
        take().start();

        // A file that is not a picture is refused, and nothing is added
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='item-photo']")))
                .sendKeys(notAnImage.toAbsolutePath().toString());
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-item-id] p.text-destructive")));
        assertThat(driver.findElements(By.cssSelector("[data-testid='item-photo-page']"))).isEmpty();

        for (int page = 1; page <= 5; page++) {
            driver.findElement(By.cssSelector("[data-testid='item-photo']")).sendKeys(photo.toAbsolutePath().toString());
            wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid='item-photo-page']"), page));
        }
        waitForText("Ai 5 pagini, cât se poate trimite. Șterge una ca să pui alta în locul ei.");
        assertThat(driver.findElements(By.cssSelector("[data-testid='item-photo']"))).as("no 6th picker").isEmpty();

        // The server refuses a 6th all the same (another tab, an old page)
        ApiSeeder student = asStudent();
        long attemptId = student.myAttempts(quizId).get(0).get("attemptId").asLong();
        HttpResponse<String> sixth = student.uploadAnswerPhoto(attemptId, open, photo, "image/png");
        assertThat(sixth.statusCode()).isEqualTo(400);
        assertThat(sixth.body()).contains("Ai deja 5 poze la această întrebare.");

        // Deleting one brings the picker back
        driver.findElement(By.cssSelector("button[aria-label='Șterge pagina 5']")).click();
        wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid='item-photo-page']"), 4));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='item-photo']")));
    }

    @Test
    void aPractiseShowsTheVerdictTheMistakeTheHintsAndTheEnLesson() throws Exception {
        String code = "E2E" + (System.nanoTime() % 10_000_000);
        String lesson = "Fracții E2E " + code;
        long chapter = admin.createEnLesson(code, lesson);
        try {
            long quizId = admin.createQuiz(quizTitle(), classId, null, true, null);
            Map<String, Object> grila = grila(0, "Cât face $\\frac{1}{2}+\\frac{1}{4}$?", 5, 0, "trei sferturi", "două șesimi");
            @SuppressWarnings("unchecked")
            Map<String, Object> wrong = ((List<Map<String, Object>>) grila.get("options")).get(1);
            wrong.put("mistake", "Ai adunat numărătorii și numitorii între ei.");
            grila.put("solution", "Aducem la numitorul 4: $\\frac{2}{4}+\\frac{1}{4}=\\frac{3}{4}$.");
            grila.put("hints", List.of("Ce numitor comun au 2 și 4?", "$\\frac{1}{2}=\\frac{2}{4}$"));
            grila.put("enChapterId", chapter);
            admin.addItem(quizId, grila);
            admin.addItem(quizId, new HashMap<>(Map.of("type", "OPEN", "position", 1, "points", 5, "enChapterId", chapter,
                    "statement", "Calculează $\\frac{1}{3}+\\frac{1}{6}$ și fotografiază rezolvarea.",
                    "solution", "Barem: numitorul comun 6, rezultatul $\\frac{1}{2}$.")));
            admin.publish(quizId);

            login(email, STUDENT_PASSWORD);
            open("/quizzes/" + quizId + "/take?mode=practice");
            assertThat(byTestId("quiz-start").getText()).isEqualTo("Începe exersarea");
            take().start();
            byTestId("quiz-practice-banner");
            WebElement first = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("(//*[@data-item-id])[1]")));

            // The hints, one at a time
            first.findElement(By.cssSelector("[data-testid='hint-button']")).click();
            wait.until(ExpectedConditions.textToBePresentInElement(first, "Indiciul 1 din 2"));
            assertThat(first.getText()).contains("Ce numitor comun au 2 și 4?").doesNotContain("Indiciul 2 din 2");
            wait.until(ExpectedConditions.textToBePresentInElement(
                    first.findElement(By.cssSelector("[data-testid='hint-button']")), "Încă un indiciu (1/2)"));
            first.findElement(By.cssSelector("[data-testid='hint-button']")).click();
            wait.until(ExpectedConditions.textToBePresentInElement(first, "Indiciul 2 din 2"));
            assertThat(first.findElements(By.cssSelector("[data-testid='hint-button']"))).as("no hint left").isEmpty();

            // A wrong variant: the verdict, the mistake behind it, the solution, the lesson
            take().choose("două șesimi");
            WebElement feedback = wait.until(ExpectedConditions.visibilityOfNestedElementsLocatedBy(
                    first, By.cssSelector("[data-testid='item-feedback']"))).get(0);
            assertThat(feedback.getText()).contains("✗ Greșit — răspunsul corect e a), marcat cu ✓.")
                    .contains("Rezolvare").contains("Aducem la numitorul 4");
            assertThat(feedback.findElement(By.cssSelector("[data-testid='item-mistake']")).getText())
                    .contains("De ce e greșit").contains("Ai adunat numărătorii și numitorii între ei.");
            WebElement link = feedback.findElement(By.cssSelector("[data-testid='revision-note'] a"));
            assertThat(link.getText()).contains("Vezi lecția: " + code + " " + lesson);
            assertThat(link.getAttribute("target")).isEqualTo("_blank");

            // The lesson opens in a new tab; the practice stays where it was
            String practiceTab = driver.getWindowHandle();
            link.click();
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            String lessonTab = driver.getWindowHandles().stream().filter(h -> !h.equals(practiceTab)).findFirst().orElseThrow();
            driver.switchTo().window(lessonTab);
            wait.until(ExpectedConditions.urlContains("/capitole/" + chapter));
            waitForText(lesson);
            driver.close();
            driver.switchTo().window(practiceTab);
            assertThat(driver.getCurrentUrl()).contains("/quizzes/" + quizId + "/take");

            // The open question: after the photo, the barem to compare with, and the lesson under it
            take().uploadPhoto(samplePng());
            WebElement second = driver.findElement(By.xpath("(//*[@data-item-id])[2]"));
            wait.until(ExpectedConditions.textToBePresentInElement(second, "Barem — compară-l cu rezolvarea ta"));
            assertThat(second.getText()).contains("numitorul comun 6");
            assertThat(second.findElement(By.cssSelector("[data-testid='revision-note'] a")).getText())
                    .contains("Vezi lecția: " + code);
        } finally {
            admin.deleteEnLesson(chapter);
        }
    }

    /** Slow (about a minute and a half): the clock of a one-minute test has to run out. Skip with -DexcludedGroups=slow. */
    @Test
    @Tag("slow")
    void theClockKeepsRunningAfterLeavingAndHandsTheTestInWhenItRunsOut() {
        long quizId = admin.createQuiz(quizTitle(), classId, 1, false, null);
        admin.addItem(quizId, grila(0, "Cât face $2+2$?", 5, 0, "patru", "cinci"));
        admin.addItem(quizId, grila(1, "Cât face $3+3$?", 5, 0, "șase", "șapte"));
        admin.publish(quizId);

        login(email, STUDENT_PASSWORD);
        open("/quizzes/" + quizId + "/take");
        assertThat(byTestId("quiz-limit-note").getText()).contains("Testul are limită de timp: 1 minut");
        take().start();
        assertThat(byTestId("quiz-timer").getText()).startsWith("⏱ Timp rămas: ");
        take().choose("patru");

        // Leaving warns that the clock does not stop
        byTestId("quiz-leave").click();
        WebElement warning = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role='alertdialog'], [role='dialog']")));
        assertThat(warning.getText()).contains("Ieși din test?").contains("Cronometrul nu se oprește");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/quizzes"));

        // Back in: the time left is said before continuing
        open("/quizzes/" + quizId + "/take");
        assertThat(byTestId("quiz-limit-note").getText())
                .containsAnyOf("mai ai 1 minut", "mai ai mai puțin de un minut");
        assertThat(byTestId("quiz-start").getText()).isEqualTo("Continuă testul");
        take().start();

        // Nothing else is touched: at 0 the page hands in what was saved
        new WebDriverWait(driver, Duration.ofSeconds(100)).until(ExpectedConditions.urlMatches("/quizzes/attempts/\\d+/result"));
        JsonNode attempt = asStudent().myAttempts(quizId).get(0);
        assertThat(attempt.get("status").asString()).isEqualTo("GRADED");
        assertThat(attempt.get("score").asInt()).as("the answer saved in time counts").isEqualTo(5);
    }

    @Test
    void acasaPutsTheHomeworkInTheUrmeazaCard() {
        String title = quizTitle();
        long quizId = admin.createQuiz(title, classId, null, false, null);
        admin.addItem(quizId, grila(0, "Cât face $2+2$?", 5, 0, "patru", "cinci"));
        admin.publish(quizId);
        admin.assign(quizId, classId, Instant.now().plus(2, ChronoUnit.DAYS));

        login(email, STUDENT_PASSWORD);
        WebElement hero = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='assignment-card'][.//p[normalize-space()='Urmează']]")));
        assertThat(hero.getText()).contains(title).contains("Termen:");
        hero.findElement(By.xpath(".//a[normalize-space()='Începe']")).click();

        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/quizzes/" + quizId + "/take"));
        assertThat(byTestId("quiz-homework-due").getText()).startsWith("Temă — termen:");
        assertThat(asStudent().myAttempts(quizId)).as("only opened, not started").isEmpty();
    }
}
