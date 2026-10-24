package ro.mathlms.gdpr;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly: archives past their date lose their bytes (retention is the mitigation, not encryption). */
@Component
public class GdprExportPurgeJob {

    private final GdprExportRequests requests;

    public GdprExportPurgeJob(GdprExportRequests requests) {
        this.requests = requests;
    }

    @Scheduled(cron = "${app.gdpr.export-purge-cron:0 15 4 * * *}")
    public void purge() {
        requests.purgeExpired();
    }
}
