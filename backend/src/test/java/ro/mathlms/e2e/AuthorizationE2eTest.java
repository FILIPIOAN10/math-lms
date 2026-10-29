package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1-A4, A7: IDOR and Role Authorization.
 * Ensures users cannot access pages, data, or APIs belonging to other roles or other users.
 */
class AuthorizationE2eTest extends E2eBase {

    @Test
    void accessControlPreventsIdorAndRoleViolations() {
        long stamp = System.currentTimeMillis();
        String quizTitle = "E2E IDOR " + stamp;

        // ==========================================
        // SETUP: Generăm datele (Test + o lucrare predată de un alt elev)
        // ==========================================
        ApiSeeder adminApi = new ApiSeeder(API_URL);
        adminApi.login(ADMIN, PASSWORD);
        long classId = adminApi.createClass("E2E clasa IDOR " + stamp);
        adminApi.ensureActiveStudent(STUDENT2);
        adminApi.enroll(classId, STUDENT2);
        long quizId = adminApi.createPublishedQuiz(quizTitle, classId);
        long student2Id = adminApi.getUserIdForTest("STUDENT", STUDENT2);

        // STUDENT2 (Radu) dă testul, generând un `attempt` pe care Ana nu ar trebui să-l vadă
        ApiSeeder student2Api = new ApiSeeder(API_URL);
        student2Api.login(STUDENT2, PASSWORD);
        long attemptId = student2Api.takeAndSubmit(quizId, "Varianta B");

        // ==========================================
        // TEST A1, A4, A7 (STUDENT - Ana)
        // ==========================================
        login(STUDENT, PASSWORD);

        // A1: Ana deschide /admin/grading -> blocat (RoleRoute/AdminRoute face redirect la /)
        open("/admin/grading");
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));

        // A4: Ana deschide rezultatul lui Radu -> refuzat
        open("/quizzes/attempts/" + attemptId + "/result");
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlToBe(BASE_URL + "/"),
                ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "nu ai acces la el")
        ));
        assertThat(bodyText()).doesNotContain(quizTitle);

        // A7: Ana face request direct prin fetch() către un API de admin -> 403 Forbidden
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Long status = (Long) js.executeAsyncScript(
                "var callback = arguments[arguments.length - 1];" +
                        "fetch('/api/admin/audit-log').then(r => callback(r.status)).catch(e => callback(0));"
        );
        assertThat(status).isEqualTo(403L);

        logout();

        // ==========================================
        // TEST A2, A3 (PARENT - Maria)
        // ==========================================
        login(PARENT, PASSWORD);

        // A2: Maria deschide /quizzes -> blocat (RoleRoute face redirect la /)
        open("/quizzes");
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));

        // A3: Maria deschide pagina unui copil care nu îi e asociat -> refuzat
        open("/parent/children/" + student2Id);
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlToBe(BASE_URL + "/"),
                ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "nu e copilul tău")
        ));

        assertThat(bodyText()).doesNotContain(STUDENT2_NAME); // Numele copilului nu trebuie să apară pe ecran
        assertThat(bodyText()).doesNotContain("Pe capitole"); // Verificăm că secțiunea de date e ascunsă

        logout();
    }
}