package ro.mathlms.audit;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.audit.AuditDtos.AuditPageDto;

import java.time.LocalDate;

/** F1: the teachers' journal, read-only (rows are written by the services that make the changes). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLog auditLog;

    public AuditLogController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping("/api/admin/audit-log")
    public AuditPageDto page(@RequestParam(required = false) AuditCategory category,
                             @RequestParam(required = false) Long studentId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) Long before,
                             @RequestParam(required = false) Integer limit) {
        return auditLog.page(category, studentId, from, to, before, limit);
    }
}
