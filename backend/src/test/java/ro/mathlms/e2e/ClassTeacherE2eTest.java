package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 3, the class's teacher: a new teacher starts at "Primii pași (0 din 4)"; a class the admin hands them with
 * "Schimbă profesorul" becomes theirs (the first step is ticked) and stays out of another teacher's sight. Needs Mailpit
 * for the new teacher.
 */
class ClassTeacherE2eTest extends E2eBase {

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void aClassHandedToANewTeacherIsTheirsAndNoOneElses() {
        String email = freshEmail("profclasa");
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        new ApiSeeder(API_URL).signUp(admin.teacherInvite(), "Profesor Clasa", email, "TestProf3!");
        new ApiSeeder(API_URL).confirmEmail(Mailbox.linkTo(email, "/verify-email"));
        admin.approve(email, "TEACHER");
        String className = "E2E clasa " + System.nanoTime();
        admin.createClass(className);

        login(email, "TestProf3!");
        waitForText("Primii pași (0 din 4)");
        logout();

        login(ADMIN, PASSWORD);
        open("/admin/content");
        chooseFromMenu(className, "Schimbă profesorul");
        new Select(byTestId("class-teacher-select")).selectByVisibleText("Profesor Clasa");
        clickText("Salvează");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.xpath("//*[@data-testid='class-card'][.//p[normalize-space()='" + className + "']]"),
                "Profesor: Profesor Clasa"));
        logout();

        login(email, "TestProf3!");
        waitForText("Primii pași (1 din 4)");
        open("/admin/content");
        waitForText(className);
        logout();

        login(TEACHER, PASSWORD);
        open("/admin/content");
        waitForText("Clase");
        assertThat(bodyText()).doesNotContain(className);
    }
}
