package ro.mathlms.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Base for browser tests against an already-running stack (Postgres + Redis, backend :8080,
 * Vite :5173) seeded with the accounts from docs/TESTING.md. Excluded from `./mvnw test`
 * by the "e2e" tag; run with: ./mvnw test -Dgroups=e2e -DexcludedGroups=
 */
@Tag("e2e")
abstract class E2eBase {

    static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://localhost:5173");
    static final String PASSWORD = "Admin123!"; // seed password from docs/TESTING.md
    static final String ADMIN = "admin@mathlms.local";
    static final String STUDENT = "student.activ@mathlms.local";

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeEach
    void startBrowser() {
        ChromeOptions options = new ChromeOptions();
        // -De2e.headless=false opens a visible Chrome so you can watch the test run
        if (!"false".equals(System.getProperty("e2e.headless"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1280,900");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void stopBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void open(String path) {
        driver.get(BASE_URL + path);
    }

    /** Logs in through the real login form (email/password tab). Ends on the dashboard. */
    protected void login(String email, String password) {
        open("/login");
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@role='tab' and normalize-space()='Email și parolă']"))).click();
        driver.findElement(By.cssSelector("input[type=email]")).sendKeys(email);
        driver.findElement(By.cssSelector("input[type=password]")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));
    }

    /**
     * Logs out through the dashboard button so the server revokes the session and clears BOTH cookies.
     * (deleteAllCookies() misses the path-scoped refresh cookie, and the SPA would silently log back in.)
     */
    protected void logout() {
        open("/");
        byTestId("logout").click();
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    protected WebElement byTestId(String testId) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[data-testid='" + testId + "']")));
    }
}
