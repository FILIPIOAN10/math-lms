package ro.mathlms.e2e;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** /quizzes — "Testele mele". */
class StudentQuizzesPage extends Page {

    StudentQuizzesPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    /** Opens the quiz with this title (the list can hold several quizzes). */
    void openQuiz(String title) {
        driver.get(E2eBase.BASE_URL + "/quizzes");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[@data-testid='quiz-card'][contains(normalize-space(.), '" + title + "')]"
                        + "//*[@data-testid='quiz-open']"))).click();
    }
}
