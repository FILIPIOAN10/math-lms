package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 3 of the manual test guide on the Clase page: a class is made and edited, students are added to it and taken
 * out, and a student sees only the classes they are in; a book, a chapter and exercises with LaTeX reach the student;
 * what still holds something is not deleted, and says why; the pages fit a phone. Everything is made in "E2E clasa"
 * classes with "E2E carte" books, which deploy/dev-cleanup-e2e.sql removes.
 */
class ClassesAndContentE2eTest extends E2eBase {

    private static By classCard(String name) {
        return By.xpath("//*[@data-testid='class-card'][.//p[normalize-space()='" + name + "']]");
    }

    /** A class, book or chapter row (Deschide, and ⋯ for the teacher), found by its title - on Clase and on Conținut. */
    private static By row(String title) {
        return By.xpath("//*[normalize-space(text())='" + title + "']/ancestor::*[@data-slot='card'][1]");
    }

    private void typeInto(String id, String text) {
        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
        input.sendKeys(text);
    }

    private void open(By rowOrCard) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(rowOrCard))
                .findElement(By.xpath(".//button[normalize-space()='Deschide']")).click();
    }

    private By dialog() {
        return By.cssSelector("[role='dialog']");
    }

    private void closeDialog() {
        new Actions(driver).sendKeys(Keys.ESCAPE).perform();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(dialog()));
    }

    @Test
    void aClassIsMadeEditedAndHoldsTheStudentsAddedToIt() {
        String name = "E2E clasa " + System.nanoTime();
        login(ADMIN, PASSWORD);
        open("/admin/content");

        clickText("Adaugă clasă");
        typeInto("name", name);
        typeInto("description", "Prima descriere");
        clickText("Salvează");
        assertThat(wait.until(ExpectedConditions.visibilityOfElementLocated(classCard(name))).getText())
                .contains("Prima descriere");

        chooseFromMenu(name, "Editează");
        typeInto("description", "Clasa de test");
        clickText("Salvează");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(classCard(name), "Clasa de test"));

        chooseFromMenu(name, "Elevii clasei");
        WebElement roster = wait.until(ExpectedConditions.visibilityOfElementLocated(dialog()));
        wait.until(ExpectedConditions.textToBePresentInElement(roster, "Niciun elev înscris."));
        roster.findElement(By.xpath(".//label[contains(normalize-space(), '(" + STUDENT + ")')]//input")).click();
        clickText("Adaugă selectații (1)");
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='dialog']//li[.//a[normalize-space()='" + STUDENT_NAME + "']]")));

        clickText("Selectează tot");
        roster.findElement(By.xpath(".//button[starts-with(normalize-space(), 'Adaugă selectații')]")).click();
        wait.until(ExpectedConditions.textToBePresentInElement(roster, "Toți elevii activi sunt deja în această clasă."));

        roster.findElement(By.xpath(".//li[.//a[normalize-space()='" + STUDENT_NAME + "']]//button[normalize-space()='Scoate']")).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(
                By.xpath("//*[@role='dialog']//li[.//a[normalize-space()='" + STUDENT_NAME + "']]")));
        closeDialog();
    }

    @Test
    void aStudentSeesOnlyTheClassesTheyAreInUntilTakenOut() {
        long stamp = System.nanoTime();
        String mine = "E2E clasa " + stamp;
        String other = "E2E clasa " + (stamp + 1);
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long mineId = admin.createClass(mine);
        admin.createClass(other);
        admin.enroll(mineId, STUDENT);

        login(STUDENT, PASSWORD);
        open("/content");
        waitForText(mine);
        assertThat(bodyText()).doesNotContain(other);
        logout();

        login(ADMIN, PASSWORD);
        open("/admin/content");
        chooseFromMenu(mine, "Elevii clasei");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[@role='dialog']//li[.//a[normalize-space()='" + STUDENT_NAME + "']]//button[normalize-space()='Scoate']"))).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(
                By.xpath("//*[@role='dialog']//li[.//a[normalize-space()='" + STUDENT_NAME + "']]")));
        closeDialog();
        logout();

        login(STUDENT, PASSWORD);
        open("/content");
        waitForText("Conținut");
        assertThat(bodyText()).doesNotContain(mine);
    }

    @Test
    void aBookAChapterAndExercisesWithMathReachTheStudent() {
        long stamp = System.nanoTime();
        String className = "E2E clasa " + stamp;
        String book = "E2E carte " + stamp;
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        admin.enroll(admin.createClass(className), STUDENT);

        login(ADMIN, PASSWORD);
        open("/admin/content");
        open(classCard(className));
        clickText("Adaugă carte");
        typeInto("name", book);
        clickText("Salvează");
        open(row(book));
        clickText("Adaugă capitol");
        typeInto("name", "Fracții E2E");
        clickText("Salvează");
        open(row("Fracții E2E"));

        clickText("Adaugă exercițiu");
        typeInto("statement", "Calculează $\\frac{1}{2}+\\frac{1}{4}$");
        typeInto("solution", "Rezultatul este $\\frac{3}{4}$");
        clickText("Salvează");
        clickText("Adaugă exercițiu");
        typeInto("statement", "Calculează $2^3 \\cdot 2^2$");
        clickText("Salvează");
        WebElement first = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@data-slot='card'][.//*[normalize-space()='Exercițiul 1']]")));
        // The LaTeX is drawn (KaTeX), not left as "$\frac..."
        assertThat(first.findElements(By.cssSelector(".katex"))).isNotEmpty();
        assertThat(first.getText()).doesNotContain("$");

        chooseFromMenu("exercițiul 1", "Editează");
        typeInto("statement", "Calculează $\\frac{1}{3}+\\frac{1}{6}$");
        clickText("Salvează");
        chooseFromMenu("exercițiul 2", "Șterge");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Exercițiul 2']")));
        logout();

        login(STUDENT, PASSWORD);
        open("/content");
        open(row(className));
        open(row(book));
        open(row("Fracții E2E"));
        WebElement exercise = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@data-slot='card'][.//*[normalize-space()='Exercițiul 1']]")));
        assertThat(exercise.findElements(By.cssSelector(".katex"))).isNotEmpty();
        assertThat(exercise.findElement(By.cssSelector(".katex-mathml")).getAttribute("textContent")).contains("frac{1}{3}");
        assertThat(driver.findElements(By.xpath("//*[normalize-space()='Exercițiul 2']"))).isEmpty();
        exercise.findElement(By.xpath(".//button[normalize-space()='Vezi soluția']")).click();
        wait.until(ExpectedConditions.textToBePresentInElement(exercise, "Rezultatul este"));
    }

    @Test
    void whatStillHoldsSomethingIsNotDeletedAndSaysWhy() {
        long stamp = System.nanoTime();
        String className = "E2E clasa " + stamp;
        String book = "E2E carte " + stamp;
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long classId = admin.createClass(className);
        admin.enroll(classId, STUDENT);
        admin.createExercise(admin.createChapter(admin.createBook(classId, book), "Capitol plin"), "Cât face $2+2$?");

        login(ADMIN, PASSWORD);
        open("/admin/content");
        chooseFromMenu(className, "Șterge");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForText("Clasa „" + className + "” nu poate fi ștearsă: are ");
        assertThat(driver.findElements(classCard(className))).isNotEmpty();

        open(classCard(className));
        chooseFromMenu(book, "Șterge");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForText("Cartea „" + book + "” nu poate fi ștearsă: are ");

        open(row(book));
        chooseFromMenu("Capitol plin", "Șterge");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForText("Capitolul „Capitol plin” nu poate fi șters: are ");
        assertThat(driver.findElements(row("Capitol plin"))).isNotEmpty();
    }

    @Test
    void onAPhoneClaseAndAChapterFitTheScreen() {
        long stamp = System.nanoTime();
        String className = "E2E clasa " + stamp;
        String book = "E2E carte " + stamp;
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long classId = admin.createClass(className);
        admin.createExercise(admin.createChapter(admin.createBook(classId, book), "Capitol pe telefon"),
                "Un enunț mai lung, ca pe un telefon să se rupă pe mai multe rânduri: $\\frac{1}{2}+\\frac{1}{3}+\\frac{1}{4}$");

        driver.manage().window().setSize(new Dimension(375, 812));
        login(ADMIN, PASSWORD);
        open("/admin/content");
        wait.until(ExpectedConditions.visibilityOfElementLocated(classCard(className)));
        assertNoSidewaysScroll();
        open(classCard(className));
        open(row(book));
        open(row("Capitol pe telefon"));
        waitForText("Exercițiul 1");
        assertNoSidewaysScroll();
        chooseFromMenu("exercițiul 1", "Editează");
        wait.until(ExpectedConditions.visibilityOfElementLocated(dialog()));
        assertNoSidewaysScroll();
    }

    private void assertNoSidewaysScroll() {
        assertThat((Long) ((JavascriptExecutor) driver).executeScript(
                "return document.documentElement.scrollWidth - document.documentElement.clientWidth"))
                .as("the page scrolls sideways on a phone").isLessThanOrEqualTo(0L);
    }
}
