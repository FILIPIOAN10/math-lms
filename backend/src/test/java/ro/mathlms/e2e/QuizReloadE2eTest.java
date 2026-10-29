package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2: Reîncărcare în timpul testului.
 * Verifică dacă răspunsurile sunt păstrate în sesiune/stare atunci când
 * elevul reîncarcă pagina (F5) accidental sau din cauza unei probleme de rețea.
 */
class QuizReloadE2eTest extends E2eBase {

    @Test
    void raspunsurileSuntPastrateDupaRefresh() {
        long stamp = System.currentTimeMillis();
        String quizTitle = "E2E reload " + stamp;

        // 1. Setup: Creăm un test simplu
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long classId = admin.createClass("E2E clasa reload " + stamp);
        admin.enroll(classId, STUDENT);
        long quizId = admin.createDraftQuiz(quizTitle, classId, false);
        admin.addChoice(quizId, 1, "Cât face 1+1?", "2", "3", null);
        admin.publish(quizId);

        // 2. Studentul începe testul
        login(STUDENT, PASSWORD);
        open("/quizzes/" + quizId + "/take");

        TakeQuizPage takePage = new TakeQuizPage(driver, wait);
        takePage.start();

        // Studentul bifează răspunsul corect
        takePage.choose("2");

        // 3. Simulează apăsarea tastei F5 (Refresh / Reîncărcare pagină)
        driver.navigate().refresh();

        // 4. După reîncărcare apare ecranul de început; "Începe testul" reia încercarea existentă
        takePage.start();
        waitForText("Cât face 1+1?");

        // 5. Răspunsul ales a rămas bifat
        WebElement radio = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//label[contains(normalize-space(.), '2')]//input[@type='radio']")));
        wait.until(ExpectedConditions.elementSelectionStateToBe(radio, true));

        // Predăm lucrarea ca să finalizăm fluxul cu succes
        takePage.submit();

        // 6. A fost reluată aceeași încercare, nu s-a creat una nouă
        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(STUDENT, PASSWORD);
        assertThat(student.countMyAttempts(quizId)).isEqualTo(1);
    }
}