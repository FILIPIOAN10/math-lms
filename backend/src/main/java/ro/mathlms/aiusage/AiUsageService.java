package ro.mathlms.aiusage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The AI bill, one row per call ({@code ai_usage}): written by {@link #record} from the provider clients, read as this
 * month's totals per feature and model for the admin. Plain JDBC, its own tiny statement, outside any transaction of
 * the caller: the workers call the AI with no transaction open, and a row must survive a caller that fails afterwards.
 */
@Service
public class AiUsageService implements AiUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);
    static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");

    /** This month's totals for one feature and model. */
    public record UsageRow(String feature, String model, long calls, long inputTokens, long outputTokens) {
    }

    /** What the admin reads: since when (the 1st of this month, Romanian time) and the totals, the biggest first. */
    public record MonthUsage(Instant from, List<UsageRow> rows) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiUsageService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void record(String feature, String model, Integer inputTokens, Integer outputTokens) {
        if (inputTokens == null && outputTokens == null) {
            return; // the provider reported nothing: no row that would read as a free call
        }
        try {
            jdbc.update("INSERT INTO ai_usage (feature, model, input_tokens, output_tokens, created_at) VALUES (?, ?, ?, ?, ?)",
                    feature, model == null || model.isBlank() ? "?" : model.strip(),
                    inputTokens == null ? 0 : inputTokens, outputTokens == null ? 0 : outputTokens,
                    Timestamp.from(Instant.now(clock)));
        } catch (DataAccessException e) {
            log.warn("AI usage not recorded ({} {} {}+{} tokens): {}", feature, model, inputTokens, outputTokens,
                    e.getMessage());
        }
    }

    public MonthUsage thisMonth() {
        Instant from = LocalDate.now(clock.withZone(ZONE)).withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        List<UsageRow> rows = jdbc.query("""
                        SELECT feature, model, count(*) AS calls, sum(input_tokens) AS input_tokens,
                               sum(output_tokens) AS output_tokens
                        FROM ai_usage WHERE created_at >= ?
                        GROUP BY feature, model
                        ORDER BY sum(output_tokens) DESC, sum(input_tokens) DESC""",
                (rs, i) -> new UsageRow(rs.getString("feature"), rs.getString("model"), rs.getLong("calls"),
                        rs.getLong("input_tokens"), rs.getLong("output_tokens")),
                Timestamp.from(from));
        return new MonthUsage(from, rows);
    }
}
