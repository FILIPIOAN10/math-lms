package ro.mathlms.gdpr;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GdprControllerTest {

    private final GdprExportService exportService = mock(GdprExportService.class);
    private final GdprController controller = new GdprController(exportService);
    private final Authentication auth =
            new UsernamePasswordAuthenticationToken("elev@scoala.ro", null);

    @Test
    void exportReturnsZipForThePrincipalWithNoStore() {
        when(exportService.exportZip("elev@scoala.ro")).thenReturn("ZIP".getBytes());

        ResponseEntity<byte[]> response = controller.export(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/zip");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("my-data-export.zip");
        assertThat(response.getBody()).isEqualTo("ZIP".getBytes());
    }
}
