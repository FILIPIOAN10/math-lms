package ro.mathlms.e2e;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Path;

/** /quizzes/:id/take — start, answer, upload the photo, hand in. */
class TakeQuizPage extends Page {

    TakeQuizPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    void start() {
        byTestId("quiz-start").click();
    }

    void choose(String optionText) {
        WebElement radio = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//label[contains(normalize-space(.), '" + optionText + "')]//input[@type='radio']")));
        radio.click();
        wait.until(ExpectedConditions.elementSelectionStateToBe(radio, true));
    }

    void uploadPhoto(Path image) {
        wait.until(ExpectedConditions.presenceOfElementLocated(testId("item-photo")))
                .sendKeys(image.toAbsolutePath().toString());
        waitForBodyText("Poză încărcată"); // the server stored it
    }

    /** Presses "Trimite lucrarea", accepts the confirm, and waits for the result page. */
    void submit() {
        byTestId("quiz-submit").click();
        acceptConfirm();
        wait.until(ExpectedConditions.urlContains("/result"));
    }
}
