package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2.4b: a quiz assigned to a class is visible only to the students enrolled in that class.
 * The student is NOT enrolled yet → the quiz is missing from "Testele mele"; after the admin enrolls
 * them it shows up.
 */
class QuizVisibilityE2eTest extends E2eBase {

    private static By quizCard(String title) {
        return By.xpath("//*[@data-testid='quiz-card'][contains(normalize-space(.), '" + title + "')]");
    }

    @Test
    void aQuizForAClassAppearsOnlyAfterTheStudentIsEnrolled() {
        long stamp = System.currentTimeMillis();
        String quizTitle = "E2E clasa " + stamp;
        ApiSeeder api = new ApiSeeder(API_URL);
        api.login(ADMIN, PASSWORD);
        long classId = api.createClass("E2E clasa " + stamp);
        api.createPublishedQuiz(quizTitle, classId);

        login(STUDENT, PASSWORD);
        open("/quizzes");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "Teste disponibile"));
        assertThat(driver.findElements(quizCard(quizTitle))).as("not enrolled yet").isEmpty();

        api.enroll(classId, STUDENT);
        driver.navigate().refresh();

        wait.until(ExpectedConditions.visibilityOfElementLocated(quizCard(quizTitle)));
    }
}
