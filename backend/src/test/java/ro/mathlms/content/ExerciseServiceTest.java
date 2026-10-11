package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseServiceTest {

    private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
    private final ChapterRepository chapterRepository = mock(ChapterRepository.class);
    private final AuditLog auditLog = mock(AuditLog.class);
    private final ExerciseService service = new ExerciseService(exerciseRepository, chapterRepository, auditLog);

    private final Chapter chapter =
            new Chapter(new Book(new SchoolClass("Clasa a 9-a", null), "M1", null), "Ecuații", null);

    @Test
    void listByChapterThrowsWhenChapterMissing() {
        when(chapterRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByChapter(404L))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void createSavesUnderExistingChapter() {
        when(chapterRepository.findById(1L)).thenReturn(Optional.of(chapter));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(i -> i.getArgument(0));

        Exercise created = service.create(1L, "enunț", "sol", Difficulty.EASY);

        assertThat(created.getStatement()).isEqualTo("enunț");
        assertThat(created.getChapter()).isSameAs(chapter);
    }

    @Test
    void createThrowsWhenChapterMissing() {
        when(chapterRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(404L, "enunț", null, null))
                .isInstanceOf(ContentNotFoundException.class);
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void updateSavesWhenVersionMatches() {
        Exercise exercise = new Exercise(chapter, "vechi", null, null); // version 0
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(i -> i.getArgument(0));

        Exercise updated = service.update(1L, "nou", "$sol$", Difficulty.HARD, 0L);

        assertThat(updated.getStatement()).isEqualTo("nou");
        assertThat(updated.getDifficulty()).isEqualTo(Difficulty.HARD);
    }

    @Test
    void updateRejectsStaleVersion() {
        Exercise exercise = new Exercise(chapter, "enunț", null, null);
        ReflectionTestUtils.setField(exercise, "version", 3L); // DB moved on to v3
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));

        // Client still holds v0 -> conflict.
        assertThatThrownBy(() -> service.update(1L, "nou", null, null, 0L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(exerciseRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ContentNotFoundException.class);
        verify(exerciseRepository, never()).deleteById(any());
        verify(auditLog, never()).record(any());
    }

    // --- F1 journal ---

    @Test
    void aDeletedExerciseStaysInTheJournalByItsChapterAndOpeningWords() {
        Exercise exercise = new Exercise(chapter, "Rezolvați   ecuația\n$x^2 = 4$.", null, null);
        when(exerciseRepository.findById(7L)).thenReturn(Optional.of(exercise));

        service.delete(7L);

        verify(exerciseRepository).deleteById(7L);
        verify(auditLog).record(new AuditEvent(AuditAction.EXERCISE_DELETED, 7L, "Ecuații: Rezolvați ecuația $x^2 = 4$.",
                null, null, null));
    }

    @Test
    void anEditNamesWhatChanged() {
        Exercise exercise = new Exercise(chapter, "enunț", "vechi", Difficulty.EASY);
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(i -> i.getArgument(0));

        service.update(1L, "enunț", "nouă", Difficulty.HARD, 0L);

        verify(auditLog).record(new AuditEvent(AuditAction.EXERCISE_UPDATED, 1L, "Ecuații: enunț",
                "rezolvare, dificultate", null, null));
    }
}
