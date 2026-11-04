package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 2, group 2: the sign-up form refuses what is wrong, and an account made with a class link goes straight into
 * the class once its email is confirmed - no approval. (Stopping the link: {@link ClassInviteLinkE2eTest}.) Needs Mailpit.
 */
class ClassSignUpE2eTest extends E2eBase {

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void aStudentWithTheClassLinkIsInTheClassWithoutApproval() {
        String className = "E2E invitatie " + System.nanoTime();
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long classId = admin.createClass(className);
        String register = pathOf(admin.classInvite(classId, 2));
        String email = freshEmail("clasa");

        open(register);
        assertThat(byTestId("invite-headline").getText()).isEqualTo("Te alături clasei " + className);

        fillSignUp("Elev Invitat", email, "abc", "abc");
        refused("Parola trebuie să aibă cel puțin 8 caractere.");
        fillSignUp("Elev Invitat", email, "TestElev1!", "TestElev2!");
        refused("Parolele nu coincid.");
        fillSignUp("Elev Invitat", ADMIN, "TestElev1!", "TestElev1!");
        refused("Există deja un cont cu acest email.");
        fillSignUp("Elev Invitat", "abc", "TestElev1!", "TestElev1!");
        // The browser stops an address without @ before anything is sent
        assertThat((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('email').validity.valid")).isFalse();
        assertThat(driver.getCurrentUrl()).contains("/register");

        fillSignUp("Elev Invitat", email, "TestElev1!", "TestElev1!");
        waitForText("Deschide-l și contul tău e gata — te poți conecta imediat.");
        open(pathOf(Mailbox.linkTo(email, "/verify-email")));
        waitForText("Emailul tău a fost confirmat și contul e activ. Te poți conecta.");

        login(email, "TestElev1!");
        assertThat(byTestId("welcome").getText()).contains("Elev Invitat");
        assertThat(admin.enrolledStudentIds(classId)).containsExactly(admin.getUserIdForTest("STUDENT", email));
    }

    private void refused(String message) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='alert' and normalize-space()='" + message + "']")));
        assertThat(driver.getCurrentUrl()).contains("/register");
    }
}
