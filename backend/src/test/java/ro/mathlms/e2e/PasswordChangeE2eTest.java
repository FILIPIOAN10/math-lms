package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 1, "Contul meu" → Parola, on a fresh student (a seed account's password must not change under the other
 * tests): every refusal says why, and after the change only the new password signs in. Needs Mailpit for the
 * student's confirmation email.
 */
class PasswordChangeE2eTest extends E2eBase {

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void theFormSaysWhatIsWrongAndOnlyTheNewPasswordWorksAfter() {
        String email = freshEmail("parola");
        newStudent("Elev Parola", email, "VecheParola1!");
        login(email, "VecheParola1!");
        open("/account");

        expectRefusal("Gresita123!", "NouaParola1!", "NouaParola1!", "Parola actuală nu e corectă.");
        expectRefusal("VecheParola1!", "NouaParola1!", "AltaParola1!", "Parolele noi nu coincid.");
        expectRefusal("VecheParola1!", "abc", "abc", "Parola nouă trebuie să aibă cel puțin 8 caractere.");

        changePassword("VecheParola1!", "NouaParola1!", "NouaParola1!");
        waitForText("✓ Parola a fost schimbată. Celelalte dispozitive au fost deconectate.");
        logout();

        submitLogin(email, "VecheParola1!");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role=alert]")));
        assertThat(driver.getCurrentUrl()).contains("/login");

        login(email, "NouaParola1!");
        assertThat(byTestId("welcome").getText()).contains("Elev Parola");
    }

    /** Fills the three fields, presses "Schimbă parola" and waits for exactly this refusal. */
    private void expectRefusal(String current, String next, String repeat, String message) {
        changePassword(current, next, repeat);
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='alert' and normalize-space()='" + message + "']")));
    }

    private void changePassword(String current, String next, String repeat) {
        for (String[] input : new String[][] {{"Parola actuală", current}, {"Parola nouă", next}, {"Repetă parola nouă", repeat}}) {
            var element = field(input[0]);
            element.clear();
            element.sendKeys(input[1]);
        }
        clickText("Schimbă parola");
    }
}
