package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole product in one story: the admin's quiz exists (seeded over REST) → the student takes it
 * (multiple choice + photo) → the teacher grades the open item → the student sees the final score.
 * 5 points auto (correct choice) + 8 points from the teacher = 13 / 15.
 */
class QuizFlowE2eTest extends E2eBase {

    @Test
    void studentTakesQuizTeacherGradesStudentSeesScore() throws IOException {
        // Arrange: a unique published quiz, so reruns never collide with earlier data
        String quizTitle = "E2E flow " + System.currentTimeMillis();
        ApiSeeder api = new ApiSeeder(API_URL);
        api.login(ADMIN, PASSWORD);
        long quizId = api.createPublishedQuiz(quizTitle);

        // Student takes the quiz
        login(STUDENT, PASSWORD);
        new StudentQuizzesPage(driver, wait).openQuiz(quizTitle);
        TakeQuizPage take = new TakeQuizPage(driver, wait);
        take.start();
        take.choose("Varianta B");
        take.uploadPhoto(samplePng());
        take.submit();

        ResultPage result = new ResultPage(driver, wait);
        result.waitUntilAwaitingGrading(5);
        String resultUrl = driver.getCurrentUrl();

        // Teacher grades the open item and finalizes
        logout();
        login(ADMIN, PASSWORD);
        GradingPage grading = new GradingPage(driver, wait);
        grading.openAttempt(STUDENT_NAME, quizTitle);
        grading.givePoints(8);
        grading.finalizeGrade();

        // The teacher's statistics page now counts that one graded attempt: average 13.0 / 15 p (87 %)
        open("/admin/quizzes/" + quizId + "/stats");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("[data-testid='stats-graded']"), "1"));
        assertThat(byTestId("stats-average").getText()).contains("13.0").contains("15").contains("87%");

        // Student sees the final score
        logout();
        login(STUDENT, PASSWORD);
        driver.get(resultUrl);
        assertThat(result.finalScore()).contains("13").contains("15");

        // ...and the graded attempt now shows on the student's progress page: 13 / 15 = 87 %
        open("/progress");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("[data-testid='progress-chart']"), quizTitle));
        assertThat(byTestId("progress-chart").getText()).contains("13 / 15").contains("87%");
    }
}
