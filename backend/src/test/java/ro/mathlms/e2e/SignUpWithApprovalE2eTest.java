package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 2, group 1: an account made with the general link confirms its email, then the login says it waits until
 * the teacher approves it on Elevi ("Conturi de aprobat"); a refused one is told so at login. A confirmation link works
 * once. Needs Mailpit.
 */
class SignUpWithApprovalE2eTest extends E2eBase {

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void anAccountFromTheGeneralLinkWaitsUntilTheTeacherApprovesIt() {
        String email = freshEmail("aprobat");
        signUpWithTheGeneralLink("Elev Aprobat", email);

        approveOrReject(email, "Aprobă");

        login(email, "TestGeneral1!");
        assertThat(byTestId("welcome").getText()).contains("Elev Aprobat");
    }

    @Test
    void aRefusedAccountIsToldSoAtLogin() {
        String email = freshEmail("respins");
        signUpWithTheGeneralLink("Elev Respins", email);

        approveOrReject(email, "Respinge");

        submitLogin(email, "TestGeneral1!");
        waitForText("Contul tău a fost respins. Contactează administratorul.");
        assertThat(driver.findElements(By.cssSelector("[data-testid='welcome']"))).isEmpty();
    }

    /** Sign-up through the form, the confirmation link (twice: the second time it is refused), then a login that waits. */
    private void signUpWithTheGeneralLink(String fullName, String email) {
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        open(pathOf(admin.generalInvite("STUDENT")));
        assertThat(byTestId("invite-headline").getText()).isEqualTo("Cont de elev");
        fillSignUp(fullName, email, "TestGeneral1!", "TestGeneral1!");
        waitForText("Deschide-l ca să îți activezi contul, apoi așteaptă aprobarea profesorului.");

        String verify = pathOf(Mailbox.linkTo(email, "/verify-email"));
        open(verify);
        waitForText("Emailul tău a fost confirmat. Contul așteaptă acum aprobarea profesorului.");
        open(verify);
        waitForText("Confirmare eșuată");

        submitLogin(email, "TestGeneral1!");
        waitForText("Contul tău așteaptă aprobarea unui administrator.");
        assertThat(driver.getCurrentUrl()).contains("/login");
    }

    /** Admin, on Elevi: "Aprobă" (as the role the account asked for) or "Respinge" on the account's row. */
    private void approveOrReject(String email, String button) {
        login(ADMIN, PASSWORD);
        open("/admin/students");
        By row = By.xpath("//*[@data-testid='pending-accounts']//tr[.//*[normalize-space()='" + email + "']]");
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@data-testid='pending-accounts']//tr[.//*[normalize-space()='" + email + "']]"
                        + "//button[normalize-space()='" + button + "']"))).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(row));
        logout();
    }
}
