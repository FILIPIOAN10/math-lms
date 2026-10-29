package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chromium.ChromiumNetworkConditions;
import org.openqa.selenium.chromium.HasNetworkConditions;
import org.openqa.selenium.support.ui.ExpectedConditions;

class OfflineAnswersE2eTest extends E2eBase {

    @Test
    void answersAreQueuedOfflineAndSentWhenOnline() {
        long stamp = System.currentTimeMillis();
        String quizTitle = "E2E offline " + stamp;

        ApiSeeder api = new ApiSeeder(API_URL);
        api.login(ADMIN, PASSWORD);
        long classId = api.createClass("E2E clasa offline " + stamp);
        api.enroll(classId, STUDENT);
        long quizId = api.createDraftQuiz(quizTitle, classId, false);
        api.addChoice(quizId, 1, "Întrebare test offline", "Corect", "Greșit", null);
        api.publish(quizId);

        login(STUDENT, PASSWORD);
        open("/quizzes/" + quizId + "/take");

        TakeQuizPage takePage = new TakeQuizPage(driver, wait);
        takePage.start();

        // Oprește rețeaua
        ChromiumNetworkConditions offline = new ChromiumNetworkConditions();
        offline.setOffline(true);
        ((HasNetworkConditions) driver).setNetworkConditions(offline);

        takePage.choose("Corect");

        // Așteaptă avertismentul queue din interfață
        takePage.waitForBodyText("Încă netrimis — se trimite singur când revine internetul");

        // Pornește rețeaua
        ChromiumNetworkConditions online = new ChromiumNetworkConditions();
        online.setOffline(false);
        ((HasNetworkConditions) driver).setNetworkConditions(online);

        // Mesajul trebuie să dispară când interfața a putut face apelul cu succes
        wait.until(ExpectedConditions.invisibilityOfElementLocated(
                By.xpath("//*[contains(text(), 'Încă netrimis')]")));

        // Acum se poate preda normal
        takePage.submit();
    }
}