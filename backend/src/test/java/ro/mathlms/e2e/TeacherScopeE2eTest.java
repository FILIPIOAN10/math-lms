package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D1, D2, D5: Teachers are isolated from other teachers'/admin's classes and quizzes,
 * do not have access to the Journal or EN Chapters, while ADMIN sees everything.
 */
class TeacherScopeE2eTest extends E2eBase {

    @Test
    void teacherOnlySeesOwnClassesAndQuizzesWhileAdminSeesAll() {
        long stamp = System.currentTimeMillis();
        String adminClass = "E2E profesor " + stamp;
        String adminQuiz = "E2E profesor " + stamp;
        String teacherClass = "E2E profesor " + (stamp + 1);
        String teacherQuiz = "E2E profesor " + (stamp + 1);

        ApiSeeder adminApi = new ApiSeeder(API_URL);
        adminApi.login(ADMIN, PASSWORD);
        long adminClassId = adminApi.createClass(adminClass);
        adminApi.createPublishedQuiz(adminQuiz, adminClassId);

        ApiSeeder teacherApi = new ApiSeeder(API_URL);
        teacherApi.login(TEACHER, PASSWORD);
        long teacherClassId = teacherApi.createClass(teacherClass);
        teacherApi.createPublishedQuiz(teacherQuiz, teacherClassId);

        login(TEACHER, PASSWORD);

        open("/admin/content");
        waitForText(teacherClass);
        assertThat(bodyText()).doesNotContain(adminClass);

        open("/admin/quizzes");
        waitForText(teacherQuiz);
        assertThat(bodyText()).doesNotContain(adminQuiz);

        // D5: Verify teacher is redirected to Home when attempting to visit admin-only routes
        open("/admin/journal");
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));

        open("/admin/en-chapters");
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));

        logout();

        login(ADMIN, PASSWORD);
        open("/admin/content");
        waitForText(adminClass);
        waitForText(teacherClass);

        open("/admin/quizzes");
        waitForText(adminQuiz);
        waitForText(teacherQuiz);
    }
}