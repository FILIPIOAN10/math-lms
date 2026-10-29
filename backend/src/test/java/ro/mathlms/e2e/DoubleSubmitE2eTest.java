package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chromium.ChromiumNetworkConditions;
import org.openqa.selenium.chromium.HasNetworkConditions;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G4: Predare dublă din interfață (rețea lentă).
 * Simulăm un elev nerăbdător care dă click de mai multe ori pe o rețea lentă: dialogul se închide la
 * primul click, butonul rămâne blocat cât timp predarea e pe drum, iar pe server rămâne o singură încercare.
 */
class DoubleSubmitE2eTest extends E2eBase {

    @Test
    void dubluClickPeReteaLentaInregistreazaOSinguraIncercareFaraEroare() {
        long stamp = System.currentTimeMillis();
        String quizTitle = "E2E dublu submit " + stamp;

        // 1. Setup: Creăm un test cu o singură întrebare
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        long classId = admin.createClass("E2E clasa dublu submit " + stamp);
        admin.enroll(classId, STUDENT);
        long quizId = admin.createDraftQuiz(quizTitle, classId, false);
        admin.addChoice(quizId, 1, "1+1=?", "2", "3", null);
        admin.publish(quizId);

        // 2. Studentul deschide testul și răspunde
        login(STUDENT, PASSWORD);
        open("/quizzes/" + quizId + "/take");

        TakeQuizPage takePage = new TakeQuizPage(driver, wait);
        takePage.start();
        takePage.choose("2");

        // 3. Încetinim rețeaua artificial (3 secunde latență)
        ChromiumNetworkConditions slow = new ChromiumNetworkConditions();
        slow.setOffline(false);
        slow.setLatency(Duration.ofSeconds(3));
        slow.setDownloadThroughput(100 * 1024);
        slow.setUploadThroughput(100 * 1024);
        ((HasNetworkConditions) driver).setNetworkConditions(slow);

        // 4. Click pe "Trimite lucrarea", apoi dublu click pe confirmare
        byTestId("quiz-submit").click();
        WebElement confirmYes = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']")));
        confirmYes.click();
        try {
            confirmYes.click(); // dialogul se închide la primul click: al doilea nu mai are pe ce cădea
        } catch (Exception ignored) {
            // așteptat: butonul a dispărut odată cu dialogul
        }

        // 5. Cât timp cererea e încă pe drum (rețea lentă), butonul de predare e blocat
        By submit = By.cssSelector("[data-testid='quiz-submit']");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(submit, "Se trimite..."));
        assertThat(driver.findElement(submit).isEnabled()).isFalse();
        try {
            driver.findElement(submit).click(); // încă o încercare de predare: nu trebuie să plece nimic
        } catch (Exception ignored) {
            // un buton dezactivat poate refuza click-ul - tot bine
        }

        // 6. Rețeaua revine la normal; predarea ajunge la rezultat
        ChromiumNetworkConditions normal = new ChromiumNetworkConditions();
        normal.setOffline(false);
        normal.setLatency(Duration.ofMillis(10));
        normal.setDownloadThroughput(100 * 1024 * 1024);
        normal.setUploadThroughput(100 * 1024 * 1024);
        ((HasNetworkConditions) driver).setNetworkConditions(normal);

        wait.until(ExpectedConditions.urlContains("/result"));
        waitForText("Notat");

        // 7. O singură încercare pe server
        ApiSeeder student = new ApiSeeder(API_URL);
        student.login(STUDENT, PASSWORD);
        assertThat(student.countMyAttempts(quizId)).isEqualTo(1);
    }
}