package ro.mathlms.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} (the outbox dispatcher and purger). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
