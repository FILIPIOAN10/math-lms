package ro.mathlms.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** The container/deploy health gate reads /actuator/health: it must depend on the database and cache, never on SMTP. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HealthIndicatorsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void anSmtpOutageCannotMarkTheBackendUnhealthy() {
        assertThat(context.containsBean("mailHealthContributor")).isFalse();
    }

    @Test
    void theDatabaseAndTheCacheStillCountTowardsHealth() {
        assertThat(context.containsBean("dbHealthContributor")).isTrue();
        assertThat(context.containsBean("redisHealthContributor")).isTrue();
    }
}
