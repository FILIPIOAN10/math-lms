package ro.mathlms.assignment;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the SQL statements Hibernate prepares on the current thread, so a test can tell how many times one call asks
 * the database - the schedulers' own queries on other threads never get in the count. Registered through
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} by the tests that need it.
 */
public class CountingStatementInspector implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int count() {
        return COUNT.get()[0];
    }
}
