package ro.mathlms.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Faza 3 settings ({@code app.ai.*}). Off unless switched on: then teachers may upload up to {@code maxPages} pages at
 * a time and {@code dailyPageLimit} a day each (the bill is per page), read by the provider named in {@code provider}.
 * Pages live in {@code pagesDir} only until the quiz is made, or {@code keepPagesDays} at most.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiImportProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("openai") String provider,
        @DefaultValue("5") int maxPages,
        @DefaultValue("50") int dailyPageLimit,
        @DefaultValue("uploads/ai-pages") String pagesDir,
        @DefaultValue("7") int keepPagesDays
) {
}
