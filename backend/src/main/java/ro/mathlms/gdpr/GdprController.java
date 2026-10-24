package ro.mathlms.gdpr;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    private final GdprExportRequests exportRequests;
    private final GdprErasureService erasureService;

    public GdprController(GdprExportRequests exportRequests, GdprErasureService erasureService) {
        this.exportRequests = exportRequests;
        this.erasureService = erasureService;
    }

    /** What asking for the archive did (the page says it); the link itself only ever goes by email. */
    public record ExportRequestedDto(GdprExportRequests.Outcome outcome) {
    }

    /** 202: queued (or already being built, or a fresh link emailed for the archive already built). */
    @PostMapping("/api/users/gdpr/export")
    public ResponseEntity<ExportRequestedDto> export(Authentication auth) {
        return ResponseEntity.accepted().body(new ExportRequestedDto(exportRequests.request(auth.getName())));
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
