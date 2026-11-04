package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 2, group 4: "Invită părintele" on Elevi makes a single-use link; the parent who signs up with it is linked to
 * that child, with no approval. A parent who already has an account is linked with "Leagă", after which the row shows
 * the parent and "Schimbă", and the same parent cannot be linked again. Fresh students and parents only: Maria and Ana
 * stay as the other tests expect them. Needs Mailpit.
 */
class ParentInviteE2eTest extends E2eBase {

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void theParentWhoSignsUpWithTheLinkSeesTheirChild() {
        String child = freshEmail("copil");
        newStudent("Copil Invitat", child, "TestCopil1!");
        String parent = freshEmail("parinte");

        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long childId = admin.getUserIdForTest("STUDENT", child);
        login(ADMIN, PASSWORD);
        open("/admin/students");
        byTestId("parent-invite-" + childId).click();
        String register = pathOf(field("Link pentru părintele lui Copil Invitat").getDomProperty("value"));
        logout();

        open(register);
        assertThat(byTestId("invite-headline").getText()).isEqualTo("Cont de părinte");
        fillSignUp("Parinte Invitat", parent, "TestParinte1!", "TestParinte1!");
        waitForText("Deschide-l și contul tău e gata — te poți conecta imediat.");
        open(pathOf(Mailbox.linkTo(parent, "/verify-email")));
        waitForText("Emailul tău a fost confirmat și contul e activ. Te poți conecta.");

        login(parent, "TestParinte1!");
        assertThat(driver.findElements(By.cssSelector("[data-testid='child-card']")))
                .singleElement().satisfies(card -> assertThat(card.getText()).contains("Copil Invitat"));
        logout();

        open(register);
        waitForText("Link indisponibil");
    }

    @Test
    void aLinkedParentShowsWithSchimbaAndTheSameParentCannotBeLinkedTwice() {
        // A parent with an account, through the API: the first child's invite
        String firstChild = freshEmail("primul");
        String parent = freshEmail("parinte");
        newStudent("Primul Copil", firstChild, "TestCopil1!");
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        new ApiSeeder(API_URL).signUp(admin.parentInvite(firstChild), "Parinte Existent", parent, "TestParinte1!");
        new ApiSeeder(API_URL).confirmEmail(Mailbox.linkTo(parent, "/verify-email"));
        String secondChild = freshEmail("aldoilea");
        newStudent("Al Doilea Copil", secondChild, "TestCopil1!");

        login(ADMIN, PASSWORD);
        open("/admin/students");
        WebElement row = rowOf("Al Doilea Copil");
        new Select(row.findElement(By.tagName("select"))).selectByVisibleText("Parinte Existent (" + parent + ")");
        row.findElement(By.xpath(".//button[normalize-space()='Leagă']")).click();

        wait.until(ExpectedConditions.textToBePresentInElement(row, "✓ Legat de Parinte Existent"));
        assertThat(row.findElements(By.tagName("select"))).isEmpty();
        assertThat(admin.parentIdOf(secondChild)).isEqualTo(admin.getUserIdForTest("PARENT", parent));

        row.findElement(By.xpath(".//button[@aria-label='Schimbă părintele lui Al Doilea Copil']")).click();
        Select list = new Select(row.findElement(By.tagName("select")));
        WebElement link = row.findElement(By.xpath(".//button[normalize-space()='Leagă']"));
        list.selectByVisibleText("Parinte Existent (" + parent + ")");
        assertThat(link.isEnabled()).isFalse();
        row.findElement(By.xpath(".//button[normalize-space()='Anulează']")).click();
        assertThat(row.findElements(By.tagName("select"))).isEmpty();
    }

    /** The Elevi table row of a student, found by the name that links to their page. */
    private WebElement rowOf(String studentName) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tr[.//a[normalize-space()='" + studentName + "']]")));
    }
}
