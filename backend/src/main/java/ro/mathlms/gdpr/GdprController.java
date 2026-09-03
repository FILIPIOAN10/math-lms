package ro.mathlms.gdpr;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service GDPR endpoints for the authenticated account, under {@code /api/users/gdpr/...}.
 * Art. 15 export is synchronous — the caller downloads their own archive directly. The account is
 * always taken from the authenticated principal, never from a parameter, so one user cannot export
 * another's data.
 */
@RestController
public class GdprController {

    private final GdprExportService exportService;

    public GdprController(GdprExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/api/users/gdpr/export")
    public ResponseEntity<byte[]> export(Authentication auth) {
        byte[] archive = exportService.exportZip(auth.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-data-export.zip\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archive);
    }
}
