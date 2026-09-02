package ro.mathlms.content;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for exercises within a chapter, with an explicit optimistic-lock check on update. */
@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ChapterRepository chapterRepository;

    public ExerciseService(ExerciseRepository exerciseRepository, ChapterRepository chapterRepository) {
        this.exerciseRepository = exerciseRepository;
        this.chapterRepository = chapterRepository;
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
        return exerciseRepository.save(new Exercise(chapter, statement, solution, difficulty));
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
        exercise.update(statement, solution, difficulty);
        return exerciseRepository.save(exercise);
    }

    @Transactional
    public void delete(Long id) {
        if (!exerciseRepository.existsById(id)) {
            throw new ContentNotFoundException("Exercise", id);
        }
        exerciseRepository.deleteById(id);
    }

    private Chapter requireChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ContentNotFoundException("Chapter", chapterId));
    }
}
