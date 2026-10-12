package ro.mathlms.e2e;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** /admin/grading — the teacher's queue and the grading screen. */
class GradingPage extends Page {

    GradingPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    /** Opens one student's submitted attempt from the "De corectat" queue. */
    void openAttempt(String studentName, String quizTitle) {
        driver.get(E2eBase.BASE_URL + "/admin/grading");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[@data-testid='attempt-row']"
                        + "[contains(normalize-space(.), '" + studentName + "') and contains(normalize-space(.), '" + quizTitle + "')]"
                        + "//*[@data-testid='attempt-open']"))).click();
    }

    /**
     * Gives the points for the (single) open item: a tap on its points button (questions up to 10 p), or the number box
     * left with Tab (above 10 p). Either saves by itself; "Salvat ✓" says the server has it.
     */
    void givePoints(int points) {
        WebElement picker = byTestId("grade-points"); // wait for the paper to be on screen before choosing how
        if ("input".equals(picker.getTagName())) {
            picker.sendKeys(Keys.chord(Keys.CONTROL, "a"), String.valueOf(points), Keys.TAB);
        } else {
            wait.until(ExpectedConditions.elementToBeClickable(testId("grade-points-" + points))).click();
        }
        wait.until(ExpectedConditions.textToBePresentInElementLocated(testId("grade-status"), "Salvat"));
    }

    /** "Finalizează nota" stays disabled until every open item has points — the click itself proves they were saved. */
    void finalizeGrade() {
        wait.until(ExpectedConditions.elementToBeClickable(testId("grade-finalize"))).click();
        acceptConfirm();
        // after finalizing, the screen closes the detail and returns to the queue: the button vanishing = done
        wait.until(ExpectedConditions.invisibilityOfElementLocated(testId("grade-finalize")));
    }
}
