package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1: the teacher mints a class link in the UI; opened by a newcomer it says which class they join; once revoked it
 * says the link is unavailable. No account is registered (that would send a real confirmation email from the dev SMTP).
 */
class ClassInviteLinkE2eTest extends E2eBase {

    @Test
    void aClassLinkNamesTheClassUntilTheTeacherStopsIt() {
        String className = "E2E invitatie " + System.currentTimeMillis();
        ApiSeeder api = new ApiSeeder(API_URL);
        api.login(ADMIN, PASSWORD);
        api.createClass(className);

        // Teacher: Clase -> the class row -> "Invită elevi" -> a link for at most 2 students
        login(ADMIN, PASSWORD);
        openInviteDialog(className);
        WebElement cap = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("invite-max-uses")));
        cap.sendKeys(Keys.chord(Keys.CONTROL, "a"), "2");
        clickText("Generează link nou");
        WebElement links = byTestId("class-invites");
        assertThat(links.getText()).contains("Folosit 0 / 2");
        String url = links.findElement(By.cssSelector("input")).getAttribute("value");
        String registerPath = "/register?" + URI.create(url).getQuery(); // the dev base URL may differ from BASE_URL

        // Newcomer (signed out): the register page says where they are going
        logout();
        open(registerPath);
        assertThat(byTestId("invite-headline").getText()).isEqualTo("Te alături clasei " + className);

        // Teacher stops the link
        login(ADMIN, PASSWORD);
        openInviteDialog(className);
        clickText("Oprește linkul");
        waitForText("Niciun link activ");

        // ...and the same address now refuses
        logout();
        open(registerPath);
        waitForText("Link indisponibil");
        assertThat(driver.findElements(By.cssSelector("[data-testid='invite-headline']"))).isEmpty();
    }

    private void openInviteDialog(String className) {
        open("/admin/content");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//p[normalize-space()='" + className + "']/ancestor::*[contains(@class,'justify-between')][1]"
                        + "//button[normalize-space()='Invită elevi']"))).click();
    }
}
