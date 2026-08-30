package ro.mathlms.auth;

import ro.mathlms.user.AccountStatus;

/**
 * Thrown after credentials check out but the account may not log in yet
 * (email not verified, or awaiting/denied admin approval). The reason is safe
 * to reveal because the caller already proved ownership with the password.
 */
public class AccountNotActiveException extends RuntimeException {

    private final String reason;

    private AccountNotActiveException(String reason) {
        super("Account not active: " + reason);
        this.reason = reason;
    }

    public static AccountNotActiveException emailNotVerified() {
        return new AccountNotActiveException("EMAIL_NOT_VERIFIED");
    }

    public static AccountNotActiveException forStatus(AccountStatus status) {
        return new AccountNotActiveException(status.name());
    }

    public String reason() {
        return reason;
    }
}
