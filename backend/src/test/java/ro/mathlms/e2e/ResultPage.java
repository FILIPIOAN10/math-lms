package ro.mathlms.e2e;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/** /quizzes/attempts/:id/result */
class ResultPage extends Page {

    ResultPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    /** Before the teacher finishes: auto-graded points are shown, the final score is not. */
    void waitUntilAwaitingGrading(int autoPoints) {
        waitForBodyText("Grilele sunt corectate (" + autoPoints + " puncte până acum)");
    }

    /** The big final score, e.g. "13 / 15 puncte" (only rendered once the attempt is GRADED). */
    String finalScore() {
        return byTestId("result-score").getText();
    }
}
