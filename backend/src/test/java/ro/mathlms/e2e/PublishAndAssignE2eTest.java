package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3: "Publică" on a draft opens the dialog that also gives it as homework - one step, one request - and the enrolled
 * student finds it under "De făcut" on their home page.
 */
class PublishAndAssignE2eTest extends E2eBase {

    @Test
    void aDraftIsPublishedAndGivenAsHomeworkInOneStep() {
        long stamp = System.currentTimeMillis();
        String className = "E2E tema " + stamp;
        String quizTitle = "E2E publica si tema " + stamp;
        ApiSeeder api = new ApiSeeder(API_URL);
        api.login(ADMIN, PASSWORD);
        long classId = api.createClass(className);
        api.enroll(classId, STUDENT);
        long quizId = api.createDraftQuiz(quizTitle, classId, false);
        api.addChoice(quizId, 0, "Cât face $2+2$?", "patru", "cinci", null);

        // Teacher: the quiz's page -> "Publică" -> the dialog has "Dă-l și ca temă" ticked, its class and a deadline
        login(ADMIN, PASSWORD);
        open("/admin/quizzes/" + quizId);
        clickText("Publică");
        clickText("Publică și dă tema");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[role='status']"), "✓"));
        assertThat(driver.findElement(By.cssSelector("[role='status']")).getText()).contains("Vezi tema");

        // Student: the homework is waiting on the home page
        logout();
        login(STUDENT, PASSWORD);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[data-testid='assignments']"), quizTitle));
        String card = driver.findElement(By.xpath(
                "//*[@data-testid='assignment-card'][contains(normalize-space(.), '" + quizTitle + "')]")).getText();
        assertThat(card).contains(className).contains("Începe");
    }
}
