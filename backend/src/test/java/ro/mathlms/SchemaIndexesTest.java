package ro.mathlms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema Flyway builds (audit 2026-11-05 L1): every single-column foreign key has an index that starts with it.
 * Postgres does not create one, and without it deleting the parent row, or listing a parent's children, scans the whole
 * child table. A new migration that adds a foreign key without its index fails here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaIndexesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(TestImages.POSTGRES);

    @Autowired
    private TestEntityManager em;

    @Test
    void everyForeignKeyHasAnIndexLeadingWithIt() {
        @SuppressWarnings("unchecked")
        List<String> unindexed = em.getEntityManager().createNativeQuery("""
                select c.conrelid::regclass || '.' || a.attname
                  from pg_constraint c
                  join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
                 where c.contype = 'f' and array_length(c.conkey, 1) = 1
                   and not exists (select 1 from pg_index i
                                    where i.indrelid = c.conrelid and i.indkey[0] = c.conkey[1])
                 order by 1""").getResultList();

        assertThat(unindexed).isEmpty();
    }
}
