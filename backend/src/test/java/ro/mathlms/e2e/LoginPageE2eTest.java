package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 1 of the manual test guide, the steps that need no email: the login tabs, the same refusal for a wrong
 * password and an unknown address, no way back in after logging out, each role kept to its pages, the 404 page and
 * the menu of each role, on a computer and on a phone.
 */
class LoginPageE2eTest extends E2eBase {

    private WebElement tab(String name) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='tab' and normalize-space()='" + name + "']")));
    }

    private static boolean selected(WebElement tab) {
        return tab.getDomAttribute("data-active") != null;
    }

    @Test
    void theChosenTabShowsAndTheMethodThatWorkedIsRemembered() {
        open("/login");
        WebElement google = tab("Google");
        WebElement password = tab("Email și parolă");
        assertThat(selected(google)).isTrue();
        assertThat(selected(password)).isFalse();
        // The selected one must look different, not only be marked: this is what broke when the style named data-selected
        assertThat(google.getCssValue("background-color")).isNotEqualTo(password.getCssValue("background-color"));

        login(ADMIN, PASSWORD);
        logout();

        assertThat(selected(tab("Email și parolă"))).isTrue();
        assertThat(selected(tab("Google"))).isFalse();
    }

    @Test
    void aWrongPasswordAndAnUnknownAddressGetTheSameAnswer() {
        assertThat(refusal(ADMIN, "gresit-" + System.nanoTime())).isEqualTo(refusal("nimeni." + System.nanoTime() + "@mathlms.local", PASSWORD));
    }

    private String refusal(String email, String password) {
        open("/login");
        tab("Email și parolă").click();
        WebElement emailInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type=email]")));
        emailInput.sendKeys(email);
        driver.findElement(By.cssSelector("input[type=password]")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        String text = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role=alert]"))).getText();
        assertThat(driver.getCurrentUrl()).contains("/login");
        return text;
    }

    @Test
    void afterLoggingOutTheBackButtonDoesNotGetBackIn() {
        login(STUDENT, PASSWORD);
        // A page reached by a click, so the browser's history holds a signed-in page to go back to
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//nav[@aria-label='Navigare principală']//a[@href='/quizzes']"))).click();
        wait.until(ExpectedConditions.urlContains("/quizzes"));
        byTestId("logout").click();
        wait.until(ExpectedConditions.urlContains("/login"));

        driver.navigate().back();

        wait.until(ExpectedConditions.urlContains("/login"));
        assertThat(driver.findElements(By.cssSelector("[data-testid='welcome']"))).isEmpty();
    }

    @Test
    void aPageOfAnotherRoleSendsHomeAndASignedOutVisitorToLogin() {
        login(STUDENT, PASSWORD);
        open("/admin/grading");
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));
        assertThat(byTestId("welcome").getText()).contains(STUDENT_NAME);
        logout();

        login(PARENT, PASSWORD);
        open("/quizzes");
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));
        logout();

        open("/admin/quizzes");
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    @Test
    void anUnknownAddressShowsThePageThatLeadsHome() {
        login(STUDENT, PASSWORD);
        open("/ceva-gresit");

        WebElement notFound = byTestId("not-found");
        assertThat(notFound.getText()).contains("Pagina nu există");
        notFound.findElement(By.xpath(".//a[normalize-space()='Înapoi acasă']")).click();
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));
    }

    @Test
    void eachRoleHasItsMenu() {
        assertThat(topMenu(ADMIN)).containsExactly("Acasă", "Clase", "Teste", "Corectură", "Elevi");
        assertThat(topMenu(TEACHER)).containsExactly("Acasă", "Clase", "Teste", "Corectură", "Elevi");
        assertThat(topMenu(STUDENT))
                .containsExactly("Acasă", "Testele mele", "Recapitulare", "Progresul meu", "Capitole EN", "Conținut");
        // A parent has one page, Acasă: no menu at all
        assertThat(topMenu(PARENT)).isEmpty();
    }

    @Test
    void onAPhoneTheStudentGetsFourTabsAndTheRestUnderMaiMult() {
        driver.manage().window().setSize(new Dimension(375, 812));
        login(STUDENT, PASSWORD);

        WebElement bar = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("nav[aria-label='Meniu principal']")));
        assertThat(firstLines(bar.findElements(By.tagName("a")))).containsExactly("Acasă", "Teste", "Recapitulare", "Progres");
        bar.findElement(By.xpath(".//button[normalize-space()='Mai mult']")).click();
        WebElement more = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("ul[aria-label='Mai mult']")));
        assertThat(firstLines(more.findElements(By.tagName("a")))).containsExactly("Capitole EN", "Conținut");
        // No sideways scrolling on a phone
        assertThat((Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return document.documentElement.scrollWidth - document.documentElement.clientWidth")).isLessThanOrEqualTo(0L);
    }

    private List<String> topMenu(String email) {
        login(email, PASSWORD);
        List<String> labels = firstLines(driver.findElements(By.cssSelector("nav[aria-label='Navigare principală'] a")));
        logout();
        return labels;
    }

    /** The label of each link, without a count badge on a second line. */
    private static List<String> firstLines(List<WebElement> links) {
        return links.stream().map(link -> link.getText().strip().split("\\R")[0].strip()).toList();
    }
}
