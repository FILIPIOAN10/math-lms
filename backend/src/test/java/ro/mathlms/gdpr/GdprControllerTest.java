package ro.mathlms.gdpr;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdprControllerTest {

    private final GdprExportRequests exportRequests = mock(GdprExportRequests.class);
    private final GdprErasureService erasureService = mock(GdprErasureService.class);
    private final GdprController controller = new GdprController(exportRequests, erasureService);
    private final Authentication auth =
            new UsernamePasswordAuthenticationToken("elev@scoala.ro", null);

    @Test
    void exportIsQueuedForThePrincipalAndNeverReturnedInTheResponse() {
        when(exportRequests.request("elev@scoala.ro")).thenReturn(GdprExportRequests.Outcome.QUEUED);

        ResponseEntity<GdprController.ExportRequestedDto> response = controller.export(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().outcome()).isEqualTo(GdprExportRequests.Outcome.QUEUED);
    }

    @Test
    void requestErasureDelegatesWithPrincipalAndPassword() {
        ResponseEntity<Void> response = controller.requestErasure(new ErasureRequest("parola123"), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(erasureService).requestErasure("elev@scoala.ro", "parola123");
    }

    @Test
    void requestErasureToleratesMissingBodyForGoogleAccounts() {
        ResponseEntity<Void> response = controller.requestErasure(null, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(erasureService).requestErasure("elev@scoala.ro", null);
    }
}
