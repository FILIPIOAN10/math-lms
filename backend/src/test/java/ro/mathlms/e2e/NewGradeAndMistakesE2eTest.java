package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The student's side of a graded EN-style test: a section heading ("SUBIECTUL I"), a figure under a statement, the
 * "Ai o notă nouă" notice (S3) that goes away once the result is opened, and "Exersează ce ai greșit" (S4) opening a
 * practice of just the question got wrong - with its heading, its number and its figure.
 */
class NewGradeAndMistakesE2eTest extends E2eBase {

    private static final String SECTION = "SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.";

    @Test
    void aNewGradeLeadsToPractisingWhatWasWrong() throws IOException {
        String quizTitle = "E2E nota noua " + System.currentTimeMillis();
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long quizId = admin.createDraftQuiz(quizTitle, null, true);
        long withFigure = admin.addChoice(quizId, 0, "Aria pătratului din figură este:", "16", "8", SECTION);
        admin.addChoice(quizId, 1, "Cât face $3 \\cdot 3$?", "9", "6", SECTION);
        admin.attachImage(withFigure, samplePng());
        admin.publish(quizId);

        // The student hands it in elsewhere (another device): question 1 wrong, question 2 right -> graded at once
        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(STUDENT, PASSWORD);
        long attemptId = student.takeAndSubmit(quizId, "8", "9");

        // S3: on the home page, and as a number next to "Testele mele"
        login(STUDENT, PASSWORD);
        WebElement notice = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='home-new-result'][contains(normalize-space(.), '" + quizTitle + "')]")));
        assertThat(notice.getText()).contains("Nou").contains("5 / 10 p");
        wait.until(ExpectedConditions.textMatches(By.cssSelector("nav a[href='/quizzes']"), java.util.regex.Pattern.compile("\\d")));
        notice.findElement(By.linkText("Vezi rezultatul")).click();

        // S4 on the result: question 1 to go back to, the wrong grilă comes back in the review, a practice of just it
        WebElement card = byTestId("practice-mistakes");
        assertThat(card.getText()).contains("Întrebarea I.1 nu a ieșit complet").contains("1 grilă greșită revine");
        assertThat(driver.getCurrentUrl()).endsWith("/quizzes/attempts/" + attemptId + "/result");
        byTestId("practice-mistakes-link").click();

        // The practice shows only that question, under its heading, with its figure
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//button[contains(normalize-space(.), 'Începe practica')]"))).click();
        assertThat(byTestId("practice-only").getText()).contains("1 întrebare");
        assertThat(byTestId("section-heading").getText()).contains("SUBIECTUL I");
        waitForText("Aria pătratului din figură este:");
        assertThat(bodyText()).doesNotContain("Cât face"); // question 2 was right: not in this practice
        WebElement figure = byTestId("question-image");
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return arguments[0].complete && arguments[0].naturalWidth > 0", figure));

        // Back home: the grade has been seen, the notice is gone
        open("/");
        byTestId("latest-grade");
        assertThat(driver.findElements(By.xpath(
                "//*[@data-testid='home-new-result'][contains(normalize-space(.), '" + quizTitle + "')]"))).isEmpty();
    }
}
