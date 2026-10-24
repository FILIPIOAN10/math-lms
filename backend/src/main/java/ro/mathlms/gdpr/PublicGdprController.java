package ro.mathlms.gdpr;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public confirmation leg of GDPR erasure, under {@code /api/public/gdpr/...}. No session is
 * required — the single-purpose token emailed in step 1 is the proof of mailbox control. Spending it
 * performs the erasure; the operation is idempotent, so a replayed token is a quiet no-op.
 */
@RestController
public class PublicGdprController {

    private final GdprErasureService erasureService;
    private final GdprExportRequests exportRequests;

    public PublicGdprController(GdprErasureService erasureService, GdprExportRequests exportRequests) {
        this.erasureService = erasureService;
        this.exportRequests = exportRequests;
    }

    /** The emailed link: spends its single-use token and streams the ZIP, never cached on the way. */
    @GetMapping("/api/public/gdpr/export/download")
    public ResponseEntity<byte[]> download(@RequestParam String token) {
        GdprExportRequests.Download download = exportRequests.download(token);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(download.zip());
    }

    @ExceptionHandler(GdprExportUnavailableException.class)
    public ResponseEntity<String> unavailable(GdprExportUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .body(ex.getMessage());
    }

    @PostMapping("/api/public/gdpr/erase/confirm")
    public ResponseEntity<Void> confirmErasure(@RequestParam String token) {
        erasureService.confirmErasure(token);
        return ResponseEntity.noContent().build();
    }
}
