package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 2, group 5: "Ai uitat parola?" answers the same for an address with an account and one without (and only
 * the real one gets an email); the link sets a new password once, after which only the new password signs in.
 * Needs Mailpit.
 */
class PasswordResetE2eTest extends E2eBase {

    private static final String SENT = "Dacă există un cont cu acest email, ți-am trimis un link de resetare.";

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void theLinkSetsANewPasswordOnceAndOnlyTheNewOneSignsIn() {
        String email = freshEmail("resetare");
        String nobody = freshEmail("nimeni");
        newStudent("Elev Resetare", email, "VecheParola1!");

        askForAReset(email);
        String link = pathOf(Mailbox.linkTo(email, "/reset-password"));
        askForAReset(nobody);
        assertThat(Mailbox.count(nobody, "/reset-password")).isZero();

        open(link);
        waitForText("Setează o parolă nouă");
        setNewPassword("abc", "abc");
        refused("Parola trebuie să aibă cel puțin 8 caractere.");
        setNewPassword("ParolaReset1!", "ParolaReset2!");
        refused("Parolele nu coincid.");
        setNewPassword("ParolaReset1!", "ParolaReset1!");
        waitForText("Parolă schimbată");
        waitForText("Parola ta a fost actualizată. Te poți conecta cu noua parolă.");

        submitLogin(email, "VecheParola1!");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role=alert]")));
        assertThat(driver.getCurrentUrl()).contains("/login");
        login(email, "ParolaReset1!");
        assertThat(byTestId("welcome").getText()).contains("Elev Resetare");
        logout();

        open(link);
        setNewPassword("AltaParola1!", "AltaParola1!");
        waitForText("Linkul de resetare este invalid sau a expirat. Cere unul nou.");
    }

    /** Login → "Ai uitat parola?" → the address → "Trimite linkul": the answer never tells whether it has an account. */
    private void askForAReset(String email) {
        open("/login");
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@role='tab' and normalize-space()='Email și parolă']"))).click();
        clickText("Ai uitat parola?");
        WebElement address = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("email")));
        address.sendKeys(email);
        clickText("Trimite linkul");
        waitForText("Verifică-ți emailul");
        waitForText(SENT);
    }

    private void setNewPassword(String password, String confirm) {
        for (String[] input : new String[][] {{"newPassword", password}, {"confirm", confirm}}) {
            WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(input[0])));
            element.clear();
            element.sendKeys(input[1]);
        }
        clickText("Schimbă parola");
    }

    private void refused(String message) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='alert' and normalize-space()='" + message + "']")));
    }
}
