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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Base for browser tests against an already-running stack (Postgres + Redis, backend :8080,
 * Vite :5173) seeded with the accounts from docs/TESTING.md. Excluded from `./mvnw test`
 * by the "e2e" tag; run with: ./mvnw test -Dgroups=e2e -DexcludedGroups=
 */
@Tag("e2e")
abstract class E2eBase {

    static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://localhost:5173");
    static final String API_URL = System.getProperty("e2e.apiUrl", "http://localhost:8080");
    static final String PASSWORD = "Admin123!"; // seed password from docs/TESTING.md
    static final String ADMIN = "admin@mathlms.local";
    static final String STUDENT = "student.activ@mathlms.local";
    static final String STUDENT_NAME = "Ana Student";
    /** The second seed student (Clasa a 9-a in the B2 dataset): for flows that need two papers. */
    static final String STUDENT2 = "student.nou@mathlms.local";
    static final String STUDENT2_NAME = "Radu Nou";
    static final String PARENT = "parinte@mathlms.local";

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

    protected void waitForText(String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), text));
    }

    protected String bodyText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    /** A button or link with exactly this visible text, once it can be clicked. */
    protected void clickText(String text) {
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[(self::button or self::a) and normalize-space()='" + text + "']"))).click();
    }

    /** A small generated PNG (a photographed solution, a figure) - no binary file in the repo. */
    static Path samplePng() throws IOException {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.BLACK);
        g.drawLine(20, 60, 280, 60);
        g.drawLine(20, 100, 200, 100);
        g.drawLine(20, 140, 120, 140);
        g.dispose();
        Path file = Files.createTempFile("e2e", ".png");
        ImageIO.write(image, "png", file.toFile());
        file.toFile().deleteOnExit();
        return file;
    }
}
