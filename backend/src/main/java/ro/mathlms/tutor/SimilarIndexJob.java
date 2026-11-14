package ro.mathlms.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * „Dă-mi una asemănătoare": every night the tutor service embeds the questions and worked examples added or changed
 * since the night before (only their statements; paid, but a few cents for a whole bank), so the search finds them the
 * next day. Nothing while the tutor is switched off. A failure is only logged: tomorrow's run catches up, and a long
 * first run the service finishes on its own even when this side stops waiting.
 */
@Component
public class SimilarIndexJob {

    private static final Logger log = LoggerFactory.getLogger(SimilarIndexJob.class);

    private final TutorProperties properties;
    private final TutorClient client;

    public SimilarIndexJob(TutorProperties properties, TutorClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Scheduled(cron = "${app.tutor.similar-index-cron:0 30 3 * * *}", zone = "Europe/Bucharest")
    public void run() {
        if (!properties.enabled()) {
            return;
        }
        try {
            TutorClient.IndexReport report = client.indexSimilar();
            log.info("Similar index: {} new, {} unchanged, {} removed", report.inserted(), report.unchanged(),
                    report.removed());
        } catch (TutorException e) {
            log.warn("Similar index not refreshed tonight: {}", e.getMessage());
        }
    }
}
