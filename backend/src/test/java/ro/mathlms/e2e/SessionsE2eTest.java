package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

class SessionsE2eTest extends E2eBase {

    @Test
    void revokingSessionLogsOutTheOtherBrowser() {
        login(STUDENT, PASSWORD);

        WebDriver secondDriver = createBrowser();
        try {
            WebDriverWait secondWait = new WebDriverWait(secondDriver, Duration.ofSeconds(10));
            secondDriver.get(BASE_URL + "/login");
            secondWait.until(ExpectedConditions.elementToBeClickable(org.openqa.selenium.By.xpath("//*[@role='tab' and normalize-space()='Email și parolă']"))).click();
            secondDriver.findElement(org.openqa.selenium.By.cssSelector("input[type=email]")).sendKeys(STUDENT);
            secondDriver.findElement(org.openqa.selenium.By.cssSelector("input[type=password]")).sendKeys(PASSWORD);
            secondDriver.findElement(org.openqa.selenium.By.cssSelector("button[type=submit]")).click();
            secondWait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));

            open("/account");
            AccountPage accountPage = new AccountPage(driver, wait);
            accountPage.revokeOtherSession();

            // Sesiunea de refresh e moartă, dar cookie-ul de acces mai e viu. Simulează expirarea lui.
            secondDriver.manage().deleteCookieNamed("MATHLMS_TOKEN");
            secondDriver.navigate().refresh();

            secondWait.until(ExpectedConditions.urlContains("/login"));

        } finally {
            secondDriver.quit();
        }
    }
}