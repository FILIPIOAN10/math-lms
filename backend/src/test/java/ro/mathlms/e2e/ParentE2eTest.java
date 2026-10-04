package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5: the admin links Ana to the parent account; Ana hands in a quiz; the parent logs in, opens
 * "Copiii mei" → Ana → the quiz → the result (worded for the parent).
 */
class ParentE2eTest extends E2eBase {

    @Test
    void aParentSeesTheirChildsQuizAndResult() {
        String quizTitle = "E2E parinte " + System.currentTimeMillis();

        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        admin.linkParent(STUDENT, PARENT);
        long quizId = admin.createPublishedQuiz(quizTitle);

        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(STUDENT, PASSWORD);
        student.takeAndSubmit(quizId, "Varianta B");

        login(PARENT, PASSWORD);
        byTestId("my-children").click();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[contains(normalize-space(.), 'Ana Student')]//*[@data-testid='child-open']"))).click();

        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[@data-testid='child-attempt'][contains(normalize-space(.), '" + quizTitle + "')]//a"))).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), quizTitle));
        assertThat(driver.getPageSource()).contains("Răspunsul elevului");
    }
}
