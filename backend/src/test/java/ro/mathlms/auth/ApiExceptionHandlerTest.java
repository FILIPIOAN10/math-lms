package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void duplicateEmailMapsToConflict() {
        ResponseEntity<String> response =
                handler.handleDuplicateEmail(new EmailAlreadyRegisteredException("ana@scoala.ro"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidTokenMapsToBadRequest() {
        ResponseEntity<String> response =
                handler.handleInvalidToken(new JwtException("JWS signature does not match key xyz"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).doesNotContain("signature"); // no internal leak
    }

    @Test
    void illegalStateMapsToConflict() {
        ResponseEntity<String> response =
                handler.handleIllegalState(new IllegalStateException("verifyEmail requires ..."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
