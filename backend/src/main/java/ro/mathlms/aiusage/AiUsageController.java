package ro.mathlms.aiusage;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** This month's AI calls and tokens, for the admin who pays the bill (not for teachers: it covers the whole app). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AiUsageController {

    private final AiUsageService service;

    public AiUsageController(AiUsageService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/ai-usage")
    public AiUsageService.MonthUsage thisMonth() {
        return service.thisMonth();
    }
}
