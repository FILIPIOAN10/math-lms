package ro.mathlms.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chapter 2, group 6 (GDPR): "Cere datele mele" emails a link to a ZIP with the account's manifest.json, good for one
 * download; "Șterge contul" asks for the password, then the emailed link erases the account for good. Erasing a parent
 * leaves the child untouched, only unlinked. Needs Mailpit.
 */
class ExportAndErasureE2eTest extends E2eBase {

    private static final String EXPORT = "/api/public/gdpr/export/download";

    @BeforeEach
    void needsMailpit() {
        assumeMailpit();
    }

    @Test
    void theExportLinkDownloadsTheAccountOnce() throws Exception {
        String email = freshEmail("export");
        newStudent("Elev Export", email, "TestExport1!");
        login(email, "TestExport1!");
        open("/account");

        clickText("Cere datele mele");
        waitForText("Pregătim arhiva.");
        String link = API_URL + pathOf(Mailbox.linkTo(email, EXPORT));

        HttpResponse<byte[]> first = download(link);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(zipEntries(first.body())).contains("manifest.json");
        assertThat(download(link).statusCode()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void aStudentErasesTheirAccountWithThePasswordAndTheEmailedLink() {
        String email = freshEmail("sters");
        newStudent("Elev Sters", email, "TestSters1!");
        login(email, "TestSters1!");

        String link = askToErase(email, "TestSters1!");
        eraseWith(link);

        submitLogin(email, "TestSters1!");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role=alert]")));
        assertThat(driver.getCurrentUrl()).contains("/login");
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        assertThat(admin.isActive("STUDENT", email)).isFalse();

        open(link);
        clickText("Șterge definitiv contul");
        waitForText("Linkul a expirat sau a fost deja folosit.");
    }

    @Test
    void erasingAParentLeavesTheChildOnlyUnlinked() {
        String child = freshEmail("copil");
        String parent = freshEmail("parinte");
        newStudent("Copil Ramas", child, "TestCopil1!");
        ApiSeeder admin = new ApiSeeder(API_URL);
        admin.login(ADMIN, PASSWORD);
        new ApiSeeder(API_URL).signUp(admin.parentInvite(child), "Parinte Sters", parent, "TestParinte1!");
        new ApiSeeder(API_URL).confirmEmail(Mailbox.linkTo(parent, "/verify-email"));
        assertThat(admin.parentIdOf(child)).isNotNull();

        login(parent, "TestParinte1!");
        eraseWith(askToErase(parent, "TestParinte1!"));

        assertThat(admin.parentIdOf(child)).isNull();
        assertThat(admin.isActive("STUDENT", child)).isTrue();
        login(child, "TestCopil1!");
        assertThat(byTestId("welcome").getText()).contains("Copil Ramas");
    }

    /** Contul meu → "Șterge contul": a wrong password is refused, the right one sends the email. Returns its link. */
    private String askToErase(String email, String password) {
        open("/account");
        field("Parola (pentru confirmare)").sendKeys("Gresita123!");
        clickText("Șterge contul");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='alert' and normalize-space()='Parola nu e corectă.']")));

        var input = field("Parola (pentru confirmare)");
        input.clear();
        input.sendKeys(password);
        clickText("Șterge contul");
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='confirm-yes']"))).click();
        waitForText("Deschide linkul din el ca să confirmi ștergerea.");
        return pathOf(Mailbox.linkTo(email, "/erase-account"));
    }

    private void eraseWith(String link) {
        open(link);
        waitForText("Ștergi contul definitiv?");
        clickText("Șterge definitiv contul");
        waitForText("Contul a fost șters");
        waitForText("Datele tale personale au fost șterse.");
    }

    private static HttpResponse<byte[]> download(String link) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(link)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static List<String> zipEntries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
