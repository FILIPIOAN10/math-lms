package ro.mathlms.e2e;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Base of the Page Objects: a page exposes what a user DOES, never CSS selectors. */
abstract class Page {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    Page(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    protected static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    protected WebElement byTestId(String id) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(testId(id)));
    }

    /** window.confirm() popups: wait for it, then press OK. */
    protected void acceptConfirm() {
        wait.until(ExpectedConditions.alertIsPresent()).accept();
    }

    protected void waitForBodyText(String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), text));
    }
}
