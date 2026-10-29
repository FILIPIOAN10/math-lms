package ro.mathlms.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorE2eTest extends E2eBase {

    @Test
    void twoFactorFlowAndRecoveryCodes() {
        login(ADMIN_2FA, PASSWORD);
        open("/account");

        AccountPage accountPage = new AccountPage(driver, wait);

        String secret = accountPage.enableTwoFactorAndGetSecret();
        accountPage.submitTwoFactorCode(TotpCodes.generate(secret));

        List<String> recoveryCodes = accountPage.getRecoveryCodes();
        assertThat(recoveryCodes).isNotEmpty();
        String firstRecoveryCode = recoveryCodes.get(0);
        logout();

        // 4. Test login cu TOTP corect (următorul pas ca să nu dea reject pe `step <= lastStep`)
        loginWithCode(ADMIN_2FA, PASSWORD, TotpCodes.generateNext(secret));
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));
        logout();

        // 5. Test login cu Cod de Recuperare
        loginWithCode(ADMIN_2FA, PASSWORD, firstRecoveryCode);
        wait.until(ExpectedConditions.urlToBe(BASE_URL + "/"));
        logout();

        // 6. Test login cu Același Cod de Recuperare (refuzat)
        loginWithCode(ADMIN_2FA, PASSWORD, firstRecoveryCode);

        waitForText("Codul nu se potrivește. Încearcă codul nou din aplicație.");
    }
}