package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps auth-flow exceptions to HTTP responses without leaking internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Failed credential checks — the FailedLoginBruteForce alert watches the rate of this counter. */
    private final Counter failedLogins;

    /** Spring uses this one; web-slice tests have no registry, so they get a throwaway one. */
    @Autowired
    public ApiExceptionHandler(ObjectProvider<MeterRegistry> meterRegistry) {
        this(meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    ApiExceptionHandler(MeterRegistry meterRegistry) {
        this.failedLogins = Counter.builder("security_failed_logins")
                .description("Failed login / credential checks")
                .register(meterRegistry); // registered up front so the series exists (at 0) before the first failure
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<String> handleDuplicateEmail(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("An account with this email already exists");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException ex) {
        failedLogins.increment();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }

    /** Credentials were correct but the account is not allowed a session yet. */
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<String> handleAccountNotActive(AccountNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.reason());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No such account");
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<String> handleInvalidToken(JwtException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired refresh token");
    }

    /** Invalid state transition, e.g. verifying an already-verified account. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("This action is not valid for the account's current state");
    }
}
