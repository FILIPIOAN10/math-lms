package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 2, group 3: a teacher invited from Elevi ("Invitații pentru profesori") signs up once, waits for the admin's
 * approval, then sees only their own (no) classes; a used or revoked link is unavailable. Needs Mailpit.
 */
class TeacherInviteE2eTest extends E2eBase {

    /** One row per active teacher link: its URL field and its "Revocă". */
    private static final By TEACHER_LINK_ROWS = By.xpath("//*[normalize-space(text())='Invitații pentru profesori']"
            + "/ancestor::*[@data-slot='card'][1]//div[contains(@class,'rounded-lg')][.//input]");

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void anInvitedTeacherIsApprovedAndSeesNoneOfTheAdminsClasses() {
        String adminClass = "E2E clasa " + System.nanoTime();
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        admin.createClass(adminClass);
        String email = freshEmail("profesor");

        login(ADMIN, PASSWORD);
        String register = pathOf(generateTeacherLink());
        logout();

        open(register);
        assertThat(byTestId("invite-headline").getText()).isEqualTo("Cont de profesor");
        fillSignUp("Profesor Doi", email, "TestProf2!", "TestProf2!");
        waitForText("Deschide-l ca să îți activezi contul, apoi așteaptă aprobarea profesorului.");
        open(pathOf(Mailbox.linkTo(email, "/verify-email")));
        waitForText("Contul așteaptă acum aprobarea profesorului.");

        // The link is spent: a second sign-up through it is refused before the form
        open(register);
        waitForText("Link indisponibil");

        admin.approve(email, "TEACHER");
        login(email, "TestProf2!");
        assertThat(byTestId("welcome").getText()).contains("Profesor Doi");
        open("/admin/content");
        waitForText("Clase");
        assertThat(bodyText()).doesNotContain(adminClass);
    }

    @Test
    void aRevokedTeacherLinkIsUnavailable() {
        login(ADMIN, PASSWORD);
        String url = generateTeacherLink();

        // The list is drawn again after "Generează": find the row afresh until it holds still
        wait.ignoring(StaleElementReferenceException.class)
                .until(d -> {
                    rowOf(url).findElement(By.xpath(".//button[normalize-space()='Revocă']")).click();
                    return true;
                });
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        wait.until(d -> linkUrls().stream().noneMatch(url::equals));
        logout();

        open(pathOf(url));
        waitForText("Link indisponibil");
    }

    /** Elevi → "Generează link sigur": the URL of the link it adds to "Linkuri active". */
    private String generateTeacherLink() {
        open("/admin/students");
        waitForText("Invitații pentru profesori");
        List<String> before = linkUrls();
        clickText("Generează link sigur");
        wait.until(d -> linkUrls().stream().anyMatch(url -> !before.contains(url)));
        return linkUrls().stream().filter(url -> !before.contains(url)).findFirst().orElseThrow();
    }

    private List<String> linkUrls() {
        try {
            return currentLinkUrls();
        } catch (StaleElementReferenceException redrawn) {
            return currentLinkUrls();
        }
    }

    private List<String> currentLinkUrls() {
        return driver.findElements(TEACHER_LINK_ROWS).stream()
                .map(row -> row.findElement(By.tagName("input")).getDomProperty("value")).toList();
    }

    private WebElement rowOf(String url) {
        return driver.findElements(TEACHER_LINK_ROWS).stream()
                .filter(row -> url.equals(row.findElement(By.tagName("input")).getDomProperty("value")))
                .findFirst().orElseThrow();
    }
}
