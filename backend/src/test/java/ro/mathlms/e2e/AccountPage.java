package ro.mathlms.e2e;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.util.stream.Collectors;

class AccountPage extends Page {

    AccountPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait);
    }

    String enableTwoFactorAndGetSecret() {
        clickText("Activează autentificarea în doi pași");
        WebElement secretEl = wait.until(ExpectedConditions.visibilityOfElementLocated(testId("two-factor-secret")));
        return secretEl.getText().replaceAll("\\s+", "");
    }

    void submitTwoFactorCode(String code) {
        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[data-testid='two-factor-section'] input[inputmode='numeric']")));
        input.sendKeys(code);
        clickText("Activează");
    }

    List<String> getRecoveryCodes() {
        WebElement codesContainer = wait.until(ExpectedConditions.visibilityOfElementLocated(testId("recovery-codes")));
        return codesContainer.findElements(By.tagName("li")).stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
    }

    void revokeOtherSession() {
        WebElement revokeBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='session-row'] button")));
        revokeBtn.click();
        // Nu are confirmare
    }
}