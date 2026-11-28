package ro.mathlms.studyplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

/**
 * Keeps each active student's estimated EN grade once a day (V84), for the plan's chart against the target. Off unless
 * {@code app.en-snapshots.enabled=true} (on in production, see .env.prod.example): it reads every student's answers,
 * so dev and tests do not run it by accident. Each student is their own transaction; one failing never stops the rest.
 */
@Component
public class EnPredictionSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(EnPredictionSnapshotJob.class);

    private final UserRepository userRepository;
    private final StudyPlanService studyPlanService;
    private final boolean enabled;

    public EnPredictionSnapshotJob(UserRepository userRepository, StudyPlanService studyPlanService,
                                   @Value("${app.en-snapshots.enabled:false}") boolean enabled) {
        this.userRepository = userRepository;
        this.studyPlanService = studyPlanService;
        this.enabled = enabled;
    }

    /** At night, Romanian time, by default: the day's answers are in and nobody is waiting on the server. */
    @Scheduled(cron = "${app.en-snapshots.cron:0 30 2 * * *}", zone = "Europe/Bucharest")
    public void tick() {
        run();
    }

    /** One run; the answer is how many estimates were kept (a student with too few answers has none yet). */
    public int run() {
        if (!enabled) {
            return 0;
        }
        int kept = 0;
        try {
            List<User> students = userRepository.findByRoleAndStatusAndErasedFalse(Role.STUDENT, AccountStatus.ACTIVE);
            for (User student : students) {
                try {
                    if (studyPlanService.snapshot(student)) {
                        kept++;
                    }
                } catch (Exception e) {
                    log.error("Keeping today's EN estimate of student {} failed", student.getId(), e);
                }
            }
            log.info("Kept today's EN estimate for {} of {} active students", kept, students.size());
        } catch (Exception e) {
            log.error("EN estimate snapshot run failed", e); // never let a bad run kill the scheduler thread
        }
        return kept;
    }
}
