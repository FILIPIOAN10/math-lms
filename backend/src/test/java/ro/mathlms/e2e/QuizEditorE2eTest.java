package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 4 of the manual test guide, the teacher's test editor (/admin/quizzes and /admin/quizzes/:id): a new test,
 * each kind of question with what it carries (variants, explained solution, hints, EN chapter, numeric answer,
 * requirements a)/b), the figure under the statement, the mistake behind a wrong variant), the question menu, "Vezi ca
 * elev", Depublică, Copiază, a test students already took, and a test made from a chapter. Everything is made in
 * "E2E clasa" classes with "E2E editor" tests (and their copies), which deploy/dev-cleanup-e2e.sql removes.
 */
class QuizEditorE2eTest extends E2eBase {

    private static final By DIALOG = By.cssSelector("[role='dialog']");

    // --- what every test needs: a class, a test in it, the editor open on it ---

    private final ApiSeeder admin = new ApiSeeder(API_URL);

    private static String quizTitle() {
        return "E2E editor " + System.nanoTime();
    }

    private long newClass() {
        admin.login(ADMIN, PASSWORD);
        return admin.createClass("E2E clasa " + System.nanoTime());
    }

    private void openEditor(long quizId) {
        login(ADMIN, PASSWORD);
        open("/admin/quizzes/" + quizId);
        byTestId("quiz-total");
    }

    private void typeInto(String id, String text) {
        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
        input.sendKeys(text);
    }

    private void typeIntoLabelled(String ariaLabel, String text) {
        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[aria-label='" + ariaLabel + "']")));
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
        input.sendKeys(text);
    }

    /** The question dialog's "Mai multe opțiuni" (figure, section, chapters, solution, hints, mistakes), unfolded. */
    private void openMoreOptions() {
        WebElement more = byTestId("item-more");
        if (more.getAttribute("open") == null) {
            more.findElement(By.tagName("summary")).click();
        }
    }

    /** The question dialog's own "Salvează", then the dialog is gone. */
    private void saveQuestion() {
        driver.findElement(DIALOG).findElement(By.xpath(".//button[@type='submit' and normalize-space()='Salvează']")).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));
    }

    /** The editor's card of question {@code label} ("1", "2"...), found by its "Editează" button. */
    private WebElement question(String label) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//button[@aria-label='Editează întrebarea " + label + "']/ancestor::*[@data-slot='card'][1]")));
    }

    /** The statements of the editor's questions, top to bottom. */
    private List<String> statementsInOrder() {
        return driver.findElements(By.xpath("//button[starts-with(@aria-label, 'Editează întrebarea ')]/ancestor::*[@data-slot='card'][1]"))
                .stream().map(card -> card.getText().contains("Prima") ? "Prima" : "A doua").toList();
    }

    private void waitForQuestions(int count) {
        wait.until(ExpectedConditions.numberOfElementsToBe(
                By.xpath("//button[starts-with(@aria-label, 'Editează întrebarea ')]"), count));
    }

    private void closeDialog() {
        new Actions(driver).sendKeys(Keys.ESCAPE).perform();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));
    }

    private long quizIdFromUrl() {
        wait.until(ExpectedConditions.urlMatches("/admin/quizzes/\\d+$"));
        String url = driver.getCurrentUrl();
        return Long.parseLong(url.substring(url.lastIndexOf('/') + 1));
    }

    // --- the tests, in the guide's order ---

    @Test
    void aNewTestStartsAsADraftWithOneAttemptForItsClassAndChapter() {
        String className = "E2E clasa " + System.nanoTime();
        String chapter = "Fracții E2E " + System.nanoTime();
        String title = quizTitle();
        admin.login(ADMIN, PASSWORD);
        long classId = admin.createClass(className);
        admin.createChapter(admin.createBook(classId, "E2E carte " + System.nanoTime()), chapter);

        login(ADMIN, PASSWORD);
        open("/admin/quizzes");
        clickText("Adaugă test");
        typeInto("qtitle", title);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//select[@id='qclass']/option[normalize-space()='" + className + "']")));
        new Select(driver.findElement(By.id("qclass"))).selectByVisibleText(className);
        new Select(driver.findElement(By.id("qchapter"))).selectByVisibleText(chapter);
        Select attempts = new Select(driver.findElement(By.id("qattempts")));
        assertThat(attempts.getFirstSelectedOption().getText()).isEqualTo("1 încercare");
        driver.findElement(By.id("qpractice")).click();
        clickText("Salvează");

        // A new test opens straight in its editor, as a draft with no questions yet
        quizIdFromUrl();
        assertThat(byTestId("quiz-total").getText()).contains("0 întrebări");
        waitForText("Nicio întrebare încă.");
        assertThat(bodyText()).contains("Ciornă").contains("Capitol: " + chapter);

        open("/admin/quizzes");
        WebElement row = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
                "//*[@data-testid='quiz-row'][.//a[normalize-space()='" + title + "']]")));
        assertThat(row.getText()).contains("Ciornă");
        assertThat(row.findElement(By.cssSelector("[data-testid='quiz-audience']")).getText())
                .isEqualTo("Clasa: " + className + " · 1 încercare · exersare permisă");
    }

    @Test
    void aGrilaKeepsItsVariantsExplainedSolutionTwoHintsAndEnChapter() {
        long quizId = admin.createDraftQuiz(quizTitle(), newClass(), true);
        openEditor(quizId);

        clickText("Adaugă întrebare");
        typeInto("istatement", "Cât face $\\frac{1}{2}+\\frac{1}{4}$?");
        typeInto("ioption-0", "$\\frac{2}{6}$");
        typeInto("ioption-1", "$\\frac{3}{4}$");
        typeInto("ioption-2", "$\\frac{1}{8}$");
        typeInto("ioption-3", "$1$");
        driver.findElement(By.cssSelector("[aria-label='Varianta b) corectă']")).click();
        typeInto("ipoints", "4");
        openMoreOptions();
        typeInto("isolution", "Aducem la numitorul 4: $\\frac{2}{4}+\\frac{1}{4}=\\frac{3}{4}$.");
        clickText("Adaugă indiciu");
        typeIntoLabelled("Indiciul 1", "Ce numitor comun au 2 și 4?");
        clickText("Adaugă indiciu");
        typeIntoLabelled("Indiciul 2", "$\\frac{1}{2}=\\frac{2}{4}$");
        Select en = new Select(driver.findElement(By.id("ienchapter")));
        assertThat(en.getOptions()).as("EN chapters (V34/V49) in the database").hasSizeGreaterThan(1);
        en.selectByIndex(1);
        String enCode = en.getFirstSelectedOption().getText().split(" ")[0];
        saveQuestion();

        WebElement card = question("1");
        assertThat(card.getText()).contains("1. Grilă · 4 p").contains("✓ b)");
        assertThat(card.findElements(By.cssSelector(".katex"))).isNotEmpty(); // the LaTeX is drawn
        assertThat(card.findElement(By.cssSelector("[data-testid='item-en-chapter']")).getText()).isEqualTo("→ " + enCode);
        assertThat(byTestId("quiz-total").getText()).contains("1 întrebare · Total: 4 p");

        // Opened again, the folded options say what the question carries
        card.findElement(By.xpath(".//button[normalize-space()='Editează']")).click();
        assertThat(byTestId("item-more-summary").getText()).contains("capitol EN").contains("rezolvare").contains("2 indicii");
        openMoreOptions();
        assertThat(driver.findElement(By.cssSelector("[aria-label='Indiciul 2']")).getAttribute("value"))
                .isEqualTo("$\\frac{1}{2}=\\frac{2}{4}$");
        closeDialog();
    }

    @Test
    void aGrilaWithoutItsRightAnswerKeepsTheTestFromBeingPublished() {
        long quizId = admin.createDraftQuiz(quizTitle(), newClass(), false);
        admin.addChoiceWithoutAnswer(quizId, 0, "Alege numărul par.");
        openEditor(quizId);

        assertThat(byTestId("no-correct-answer").getText()).contains("Fără răspuns corect");
        assertThat(byTestId("unanswered-grile").getText()).contains("1 grilă nu are încă răspunsul corect: 1");

        clickText("Publică");
        WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        dialog.findElement(By.cssSelector("button[type='submit']")).click();
        WebElement refusal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role='dialog'] [role='alert']")));
        assertThat(refusal.getText()).contains("Grila 1 nu are răspunsul corect");
        closeDialog();
        assertThat(bodyText()).contains("Ciornă");

        // In the dialog itself, a variant left empty is not saved
        clickText("Adaugă întrebare");
        typeInto("istatement", "Cât face $3 \\cdot 3$?");
        typeInto("ioption-0", "9");
        typeInto("ioption-1", "6");
        driver.findElement(DIALOG).findElement(By.xpath(".//button[@type='submit' and normalize-space()='Salvează']")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(DIALOG, "Completează textul fiecărei variante."));
        closeDialog();
        waitForQuestions(1);
    }

    @Test
    void aNumericQuestionTakesItsAnswerAsANumber() {
        long quizId = admin.createDraftQuiz(quizTitle(), newClass(), false);
        openEditor(quizId);

        clickText("Adaugă întrebare");
        new Select(driver.findElement(By.id("itype"))).selectByValue("NUMERIC");
        typeInto("istatement", "Cât face $0,5+0,25$? Scrie rezultatul ca fracție.");
        typeInto("inumeric", "trei sferturi");
        driver.findElement(DIALOG).findElement(By.xpath(".//button[@type='submit' and normalize-space()='Salvează']")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(DIALOG, "Scrie răspunsul corect ca număr"));

        typeInto("inumeric", "3/4");
        saveQuestion();
        assertThat(question("1").getText()).contains("1. Răspuns numeric · 5 p").contains("✓ Răspuns corect: 3/4");
    }

    @Test
    void anOpenQuestionAddsUpItsRequirementsAndShowsItsFigure() throws Exception {
        long quizId = admin.createDraftQuiz(quizTitle(), newClass(), false);
        openEditor(quizId);

        clickText("Adaugă întrebare");
        new Select(driver.findElement(By.id("itype"))).selectByValue("OPEN");
        typeInto("istatement", "În triunghiul din figură, $AB = 6$ și $AC = 8$.");
        // Leaving the statement typesets it and drops its live preview, which moves everything below: leave it first,
        // or the click lands where the button was a moment ago.
        driver.findElement(By.id("istatement")).sendKeys(Keys.TAB);
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button#istatement")));
        clickText("Adaugă cerința a)");
        typeIntoLabelled("Cerința a)", "Arată că triunghiul e dreptunghic.");
        clickText("Adaugă cerința b)");
        typeIntoLabelled("Cerința b)", "Calculează aria triunghiului.");
        typeIntoLabelled("Puncte cerința b)", "4");
        assertThat(driver.findElement(By.id("ipoints")).getAttribute("value")).isEqualTo("6"); // 2 + 4
        assertThat(driver.findElement(By.id("ipoints")).isEnabled()).isFalse();

        openMoreOptions();
        driver.findElement(By.cssSelector("input[aria-label='Fișierul imaginii']")).sendKeys(samplePng().toAbsolutePath().toString());
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role='dialog'] img[alt='Figura întrebării']")));
        saveQuestion();

        WebElement card = question("1");
        assertThat(card.getText()).contains("1. Cu rezolvare · 6 p")
                .contains("Arată că triunghiul e dreptunghic.").contains("Calculează aria triunghiului.");
        WebElement figure = card.findElement(By.cssSelector("[data-testid='question-image']"));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return arguments[0].complete && arguments[0].naturalWidth > 0", figure));
        assertThat(byTestId("quiz-total").getText()).contains("Total: 6 p");
    }

    @Test
    void aWrongVariantKeepsTheMistakeItHides() {
        long quizId = admin.createDraftQuiz(quizTitle(), newClass(), false);
        admin.addChoice(quizId, 0, "Cât face $\\frac{1}{2}+\\frac{1}{3}$?", "$\\frac{5}{6}$", "$\\frac{2}{5}$", null);
        openEditor(quizId);

        question("1").findElement(By.xpath(".//button[normalize-space()='Editează']")).click();
        openMoreOptions();
        assertThat(byTestId("item-mistakes").findElements(By.tagName("textarea")))
                .as("only the wrong variant b) has a mistake box").hasSize(1);
        typeIntoLabelled("Greșeala din spatele variantei b)", "Ai adunat numărătorii și numitorii între ei.");
        saveQuestion();

        question("1").findElement(By.xpath(".//button[normalize-space()='Editează']")).click();
        assertThat(byTestId("item-more-summary").getText()).contains("explicații greșeli");
        openMoreOptions();
        assertThat(driver.findElement(By.cssSelector("[aria-label='Greșeala din spatele variantei b)']")).getAttribute("value"))
                .isEqualTo("Ai adunat numărătorii și numitorii între ei.");
        closeDialog();
    }

    @Test
    void questionsAreDuplicatedMovedAndDeletedFromTheirMenu() {
        long quizId = admin.createDraftQuiz(quizTitle(), newClass(), false);
        admin.addChoice(quizId, 0, "Prima întrebare: $1+1$?", "2", "3", null);
        admin.addChoice(quizId, 1, "A doua întrebare: $2+2$?", "4", "5", null);
        openEditor(quizId);
        waitForQuestions(2);

        chooseFromMenu("întrebarea 1", "Mută mai jos");
        wait.until(d -> statementsInOrder().equals(List.of("A doua", "Prima")));
        chooseFromMenu("întrebarea 2", "Mută mai sus");
        wait.until(d -> statementsInOrder().equals(List.of("Prima", "A doua")));

        // The first question cannot go higher: its menu does not offer it
        driver.findElement(By.cssSelector("button[aria-label='Mai multe acțiuni pentru întrebarea 1']")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@role='menuitem' and normalize-space()='Duplică']")));
        assertThat(driver.findElements(By.xpath("//*[@role='menuitem' and normalize-space()='Mută mai sus']"))).isEmpty();
        new Actions(driver).sendKeys(Keys.ESCAPE).perform();

        chooseFromMenu("întrebarea 2", "Duplică");
        waitForQuestions(3);
        assertThat(statementsInOrder()).containsExactlyInAnyOrder("Prima", "A doua", "A doua");
        assertThat(byTestId("quiz-total").getText()).contains("3 întrebări · Total: 15 p");

        chooseFromMenu("întrebarea 3", "Șterge întrebarea");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(DIALOG, "Ștergi întrebarea 3?"));
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForQuestions(2);
        assertThat(statementsInOrder()).containsExactly("Prima", "A doua");
    }

    @Test
    void veziCaElevShowsTheStudentsViewAndMakesNoAttempt() throws Exception {
        String title = quizTitle();
        long quizId = admin.createDraftQuiz(title, newClass(), false);
        admin.addChoice(quizId, 0, "Cât face $2+2$?", "patru", "cinci", null);
        admin.attachImage(admin.addOpen(quizId, 1, "Rezolvă problema din figură.", 10), samplePng());
        openEditor(quizId);

        chooseFromMenu(title, "Vezi ca elev");
        wait.until(ExpectedConditions.urlContains("/admin/quizzes/" + quizId + "/preview"));
        assertThat(byTestId("preview-note").getText())
                .contains("2 întrebări, 15 p").contains("Răspunsurile corecte și baremul sunt ascunse");
        wait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector("[data-testid='preview-item']"), 2));
        assertThat(driver.findElements(By.cssSelector("[data-testid='question-image']"))).hasSize(1);
        assertThat(bodyText()).doesNotContain("Barem:");

        assertThat(admin.isTaken(quizId)).as("the preview made an attempt").isFalse();
    }

    @Test
    void depublicaSaysWhoItInterruptsAndCopiazaMakesAFreshDraft() {
        String title = quizTitle();
        long classId = newClass();
        admin.enroll(classId, STUDENT);
        long quizId = admin.createDraftQuiz(title, classId, false);
        admin.addChoice(quizId, 0, "Cât face $2+2$?", "patru", "cinci", null);
        admin.publish(quizId);
        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(STUDENT, PASSWORD);
        student.startAttempt(quizId); // Ana is writing it right now

        login(ADMIN, PASSWORD);
        open("/admin/quizzes");
        By row = By.xpath("//*[@data-testid='quiz-row'][.//a[normalize-space()='" + title + "']]");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(row, "Publicat"));
        chooseFromMenu(title, "Depublică");
        WebElement confirm = wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        wait.until(ExpectedConditions.textToBePresentInElement(confirm, "Depublici „" + title + "”?"));
        assertThat(confirm.getText()).contains("1 elev lucrează acum la el");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(row, "Ciornă"));

        chooseFromMenu(title, "Copiază");
        long copyId = quizIdFromUrl();
        assertThat(copyId).isNotEqualTo(quizId);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("h1"), title + " (copie)"));
        assertThat(byTestId("quiz-total").getText()).contains("1 întrebare · Total: 5 p");
        assertThat(bodyText()).contains("Ciornă");
        assertThat(admin.isTaken(copyId)).as("a copy starts without attempts").isFalse();
    }

    @Test
    void aTestStudentsTookIsFrozenAndOffersCopiazaSiEditeaza() {
        String title = quizTitle();
        long classId = newClass();
        admin.enroll(classId, STUDENT);
        long quizId = admin.createDraftQuiz(title, classId, false);
        admin.addChoice(quizId, 0, "Cât face $2+2$?", "patru", "cinci", null);
        admin.publish(quizId);
        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(STUDENT, PASSWORD);
        student.takeAndSubmit(quizId, "patru");

        openEditor(quizId);
        assertThat(byTestId("quiz-taken").getText()).contains("nu mai poți adăuga sau șterge întrebări");
        assertThat(button("Adaugă întrebare").isEnabled()).isFalse();
        assertThat(button("Importă din text").isEnabled()).isFalse();
        assertThat(driver.findElements(By.cssSelector("button[aria-label='Mai multe acțiuni pentru întrebarea 1']")))
                .as("no Duplică / Mută / Șterge on a taken test").isEmpty();
        assertThat(question("1").findElement(By.xpath(".//button[normalize-space()='Editează']")).isEnabled()).isTrue();

        clickText("Copiază și editează");
        wait.until(ExpectedConditions.not(ExpectedConditions.urlToBe(BASE_URL + "/admin/quizzes/" + quizId)));
        long copyId = quizIdFromUrl();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("h1"), title + " (copie)"));
        assertThat(driver.findElements(By.cssSelector("[data-testid='quiz-taken']"))).isEmpty();
        assertThat(button("Adaugă întrebare").isEnabled()).isTrue();
        assertThat(admin.isTaken(copyId)).isFalse();
    }

    @Test
    void creeazaTestDinCapitolMakesADraftFromTheTickedExercises() {
        long stamp = System.nanoTime();
        String className = "E2E clasa " + stamp;
        String book = "E2E carte " + stamp;
        String chapter = "Puteri E2E " + stamp;
        String title = quizTitle();
        admin.login(ADMIN, PASSWORD);
        long chapterId = admin.createChapter(admin.createBook(admin.createClass(className), book), chapter);
        admin.createExercise(chapterId, "Calculează $2^3 \\cdot 2^2$.");
        admin.createExercise(chapterId, "Calculează $(3^2)^2$.");

        login(ADMIN, PASSWORD);
        open("/admin/content");
        openRow(By.xpath("//*[@data-testid='class-card'][.//p[normalize-space()='" + className + "']]"));
        openRow(row(book));
        openRow(row(chapter));
        clickText("Creează test din capitol");
        typeInto("chapter-quiz-title", title);
        typeInto("chapter-quiz-points", "2");
        clickText("Creează testul (2)");

        quizIdFromUrl();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("h1"), title));
        assertThat(byTestId("quiz-total").getText()).contains("2 întrebări · Total: 4 p");
        assertThat(bodyText()).contains("Ciornă").contains("Capitol: " + chapter);
        assertThat(question("1").getText()).contains("1. Cu rezolvare · 2 p").contains("2");
        assertThat(question("2").getText()).contains("2. Cu rezolvare · 2 p");
    }

    private WebElement button(String text) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[normalize-space()='" + text + "']")));
    }

    /** A class, book or chapter row on Conținut, found by its title (as in ClassesAndContentE2eTest). */
    private static By row(String title) {
        return By.xpath("//*[normalize-space(text())='" + title + "']/ancestor::*[@data-slot='card'][1]");
    }

    private void openRow(By rowOrCard) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(rowOrCard))
                .findElement(By.xpath(".//button[normalize-space()='Deschide']")).click();
    }
}
