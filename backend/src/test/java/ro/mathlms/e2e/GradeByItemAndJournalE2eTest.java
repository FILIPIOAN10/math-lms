package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4 + F1: two students hand in the same test; the teacher grades its open question across both papers in a row
 * ("Corectează pe subiect"), finalises both at once, and the journal then shows each grade and each finished paper,
 * with the students' names.
 */
class GradeByItemAndJournalE2eTest extends E2eBase {


    @Test
    void oneQuestionAcrossTwoPapersThenTheJournalTellsWhoGotWhat() {
        String quizTitle = "E2E pe subiect " + System.currentTimeMillis();
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        admin.ensureActiveStudent(STUDENT2);
        long quizId = admin.createDraftQuiz(quizTitle, null, false);
        admin.addOpen(quizId, 0, "Demonstrează că $\\sqrt{2}$ este irațional.", 10);
        admin.publish(quizId);
        for (String email : new String[] {STUDENT, STUDENT2}) {
            ApiSeeder student = new ApiSeeder(API_URL);
            student.login(email, PASSWORD);
            student.takeAndSubmit(quizId); // nothing to pick: the open question waits for the teacher
        }

        // Teacher: the queue groups the two papers under the quiz -> "Corectează pe subiect"
        login(ADMIN, PASSWORD);
        open("/admin/grading");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[@data-testid='quiz-group'][contains(normalize-space(.), '" + quizTitle + "')]"
                        + "//button[normalize-space()='Corectează pe subiect']"))).click();

        givePoints(7, "lucrarea 1 din 2");
        clickText("Următoarea lucrare →");
        givePoints(9, "lucrarea 2 din 2");

        // The count goes 1 -> 2 when the second save comes back: wait for it rather than read it straight away
        By finalize = By.cssSelector("[data-testid='finalize-ready']");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(finalize, "(2)"));
        wait.until(ExpectedConditions.elementToBeClickable(finalize)).click();
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForText("2 lucrări finalizate");

        // The journal, filtered to grading: both grades and both finished papers, newest first
        open("/admin/journal?category=GRADING");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[data-testid='audit-row']"), quizTitle));
        String rows = driver.findElements(By.cssSelector("[data-testid='audit-row']")).stream()
                .map(WebElement::getText)
                .filter(text -> text.contains(quizTitle))
                .reduce("", (a, b) -> a + "\n---\n" + b);
        assertThat(rows)
                .contains("Punctaj dat · " + quizTitle + " · subiectul 1")
                .contains("7 p din 10").contains("9 p din 10")
                .contains("Lucrare notată · " + quizTitle)
                .contains(STUDENT_NAME).contains(STUDENT2_NAME)
                .contains("de Prof Admin");
    }

    /** Taps the points for the paper on screen (checked by its "lucrarea x din y"); the tap saves them. */
    private void givePoints(int points, String paper) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[data-testid='answer-grader']"), paper));
        wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='answer-grader'] [data-testid='grade-points-" + points + "']"))).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("[data-testid='answer-grader'] [data-testid='grade-status']"), "Salvat"));
    }
}
