package ro.mathlms.gdpr;

import org.springframework.http.ResponseEntity;
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

    public PublicGdprController(GdprErasureService erasureService) {
        this.erasureService = erasureService;
    }

    @PostMapping("/api/public/gdpr/erase/confirm")
    public ResponseEntity<Void> confirmErasure(@RequestParam String token) {
        erasureService.confirmErasure(token);
        return ResponseEntity.noContent().build();
    }
}
