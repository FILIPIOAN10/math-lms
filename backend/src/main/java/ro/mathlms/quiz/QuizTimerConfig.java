package ro.mathlms.quiz;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** The clock the quiz timer reads. A bean so tests can substitute one they control. */
@Configuration
public class QuizTimerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
