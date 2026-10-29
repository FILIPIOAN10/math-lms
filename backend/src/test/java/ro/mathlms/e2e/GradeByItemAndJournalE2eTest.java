package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4 + F1: two students hand in the same test; the teacher grades its open question across both papers in a row
 * ("Corectează pe întrebare"), using the fastest flow: KEYBOARD (number keys) and COMMENT BANK.
 */
class GradeByItemAndJournalE2eTest extends E2eBase {

    @Test
    void gradingFastWithKeyboardAndCommentBank() {
        String quizTitle = "E2E pe întrebare " + System.currentTimeMillis();
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        admin.ensureActiveStudent(STUDENT2);
        long quizId = admin.createDraftQuiz(quizTitle, null, false);
        admin.addOpen(quizId, 0, "Demonstrează că $\\sqrt{2}$ este irațional.", 10);
        admin.publish(quizId);

        for (String email : new String[] {STUDENT, STUDENT2}) {
            ApiSeeder student = new ApiSeeder(API_URL);
            student.login(email, PASSWORD);
            student.takeAndSubmit(quizId); // nothing to pick: the open question waits for the teacher
        }

        // Teacher: the queue groups the two papers under the quiz -> "Corectează pe întrebare"
        login(ADMIN, PASSWORD);
        open("/admin/grading");
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                "//*[@data-testid='quiz-group'][contains(normalize-space(.), '" + quizTitle + "')]"
                        + "//button[normalize-space()='Corectează pe întrebare']"))).click();

        String snippet = "E2E atenție la pașii de demonstrație " + System.currentTimeMillis();
        By grader = By.cssSelector("[data-testid='answer-grader']");
        By status = By.cssSelector("[data-testid='answer-grader'] [data-testid='grade-status']");
        By comment = By.cssSelector("[data-testid='answer-grader'] textarea");
        By pointsZero = By.cssSelector("[data-testid='answer-grader'] [data-testid='grade-points-0']");
        By chip = By.cssSelector("[data-testid='comment-bank'] button[title='" + snippet + "']");

        // ==========================================
        // 1. LUCRAREA 1 (tasta 7 + comentariu salvat în bancă)
        // ==========================================
        wait.until(ExpectedConditions.textToBePresentInElementLocated(grader, "lucrarea 1 din 2"));
        // I2: butonul 0 are focus automat; sendKeys îl focusează fără click, iar cifra alege punctajul
        wait.until(ExpectedConditions.visibilityOfElementLocated(pointsZero)).sendKeys("7");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(status, "Salvat"));
        assertThat(driver.findElement(By.cssSelector("[data-testid='answer-grader'] [data-testid='grade-points-7']"))
                .getAttribute("aria-checked")).isEqualTo("true");

        // I4: comentariul intră în bancă doar prin "Salvează ca frecvent"; apare apoi ca buton (chip)
        wait.until(ExpectedConditions.elementToBeClickable(comment)).sendKeys(snippet);
        clickText("⭐ Salvează ca frecvent");
        wait.until(ExpectedConditions.visibilityOfElementLocated(chip));

        clickText("Următoarea lucrare →"); // se mută doar după ce lucrarea 1 e salvată

        // ==========================================
        // 2. LUCRAREA 2 (tasta 9 + comentariu din bancă)
        // ==========================================
        wait.until(ExpectedConditions.textToBePresentInElementLocated(grader, "lucrarea 2 din 2"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(pointsZero)).sendKeys("9");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(status, "Salvat"));

        wait.until(ExpectedConditions.elementToBeClickable(chip)).click(); // inserează comentariul și salvează
        wait.until(ExpectedConditions.attributeToBe(comment, "value", snippet));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(status, "Salvat"));

        // curățenie: scoate comentariul din bancă, ca să nu rămână după test
        driver.findElement(By.cssSelector(
                "[aria-label='Scoate „" + snippet + "” din comentariile frecvente']")).click();
        waitForText("Scos din comentariile frecvente.");

        // ==========================================
        // 3. I6: FINALIZAREA NOTEI
        // ==========================================
        By finalize = By.cssSelector("[data-testid='finalize-ready']");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(finalize, "(2)"));
        wait.until(ExpectedConditions.elementToBeClickable(finalize)).click();
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForText("2 lucrări finalizate");

        // Verificare Journal
        open("/admin/journal?category=GRADING");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[data-testid='audit-row']"), quizTitle));
        String rows = driver.findElements(By.cssSelector("[data-testid='audit-row']")).stream()
                .map(WebElement::getText)
                .filter(text -> text.contains(quizTitle))
                .reduce("", (a, b) -> a + "\n---\n" + b);
        assertThat(rows)
                .contains("Punctaj dat · " + quizTitle + " · întrebarea 1")
                .contains("7 p din 10").contains("9 p din 10")
                .contains("Lucrare notată · " + quizTitle)
                .contains(STUDENT_NAME).contains(STUDENT2_NAME)
                .contains("de Prof Admin");
    }
}