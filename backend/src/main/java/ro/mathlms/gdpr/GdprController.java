package ro.mathlms.gdpr;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service GDPR endpoints for the authenticated account, under {@code /api/users/gdpr/...}.
 * Art. 15 export is synchronous — the caller downloads their own archive directly. Art. 17 erasure is
 * requested here (step 1) and confirmed via {@link PublicGdprController} (step 2). The account is
 * always taken from the authenticated principal, never a parameter, so no one can act on another's data.
 */
@RestController
public class GdprController {

    private final GdprExportService exportService;
    private final GdprErasureService erasureService;

    public GdprController(GdprExportService exportService, GdprErasureService erasureService) {
        this.exportService = exportService;
        this.erasureService = erasureService;
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

    /** Step 1 of erasure: verify the requester and email a confirmation link. Nothing is deleted yet. */
    @PostMapping("/api/users/gdpr/erase")
    public ResponseEntity<Void> requestErasure(@RequestBody(required = false) ErasureRequest request,
                                               Authentication auth) {
        String password = request == null ? null : request.password();
        erasureService.requestErasure(auth.getName(), password);
        return ResponseEntity.accepted().build();
    }
}
