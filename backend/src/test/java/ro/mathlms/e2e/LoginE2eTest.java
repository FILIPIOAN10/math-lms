package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginE2eTest extends E2eBase {

    @Test
    void adminCanLogInAndSeesTheDashboard() {
        login(ADMIN, PASSWORD);

        assertThat(byTestId("welcome").getText()).contains("Bine ai venit");
    }

    @Test
    void studentCanLogInAfterTheAdminSessionIsDropped() {
        login(ADMIN, PASSWORD);
        logout();

        login(STUDENT, PASSWORD);

        assertThat(byTestId("welcome").getText()).contains("Ana Student");
    }
}
