package ro.mathlms.content;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;

import java.util.List;

/** CRUD for exercises within a chapter, with an explicit optimistic-lock check on update (writes are journalled). */
@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ChapterRepository chapterRepository;
    private final AuditLog auditLog;

    public ExerciseService(ExerciseRepository exerciseRepository, ChapterRepository chapterRepository,
                           AuditLog auditLog) {
        this.exerciseRepository = exerciseRepository;
        this.chapterRepository = chapterRepository;
        this.auditLog = auditLog;
    }

    /** "Funcții: Fie f(x) = 2x + 3. Calculați…": an exercise has no title, so its chapter and opening words. */
    private static String label(Exercise exercise) {
        return exercise.getChapter().getTitle() + ": " + AuditText.snippet(exercise.getStatement(), 80);
    }

    public List<Exercise> listByChapter(Long chapterId) {
        requireChapter(chapterId);
        return exerciseRepository.findByChapterIdOrderById(chapterId);
    }

    public Exercise get(Long id) {
        return exerciseRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Exercise", id));
    }

    @Transactional
    public Exercise create(Long chapterId, String statement, String solution, Difficulty difficulty) {
        Chapter chapter = requireChapter(chapterId);
        Exercise saved = exerciseRepository.save(new Exercise(chapter, statement, solution, difficulty));
        auditLog.record(AuditEvent.of(AuditAction.EXERCISE_CREATED, saved.getId(), label(saved)));
        return saved;
    }

    /**
     * Updates an exercise, rejecting the write if {@code expectedVersion} no longer matches
     * the stored version — i.e. someone else edited it since the client loaded it (→ 409).
     */
    @Transactional
    public Exercise update(Long id, String statement, String solution, Difficulty difficulty,
                           long expectedVersion) {
        Exercise exercise = get(id);
        if (exercise.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(Exercise.class, id);
        }
        AuditText.Changes changes = new AuditText.Changes()
                .addIfDifferent(exercise.getStatement(), statement, "enunț")
                .addIfDifferent(exercise.getSolution(), solution, "rezolvare")
                .addIf(exercise.getDifficulty() != difficulty, "dificultate");
        exercise.update(statement, solution, difficulty);
        Exercise saved = exerciseRepository.save(exercise);
        auditLog.record(AuditEvent.of(AuditAction.EXERCISE_UPDATED, id, label(exercise)).details(changes.joined()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Exercise", id));
        exerciseRepository.deleteById(id);
        auditLog.record(AuditEvent.of(AuditAction.EXERCISE_DELETED, id, label(exercise)));
    }

    private Chapter requireChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ContentNotFoundException("Chapter", chapterId));
    }
}
