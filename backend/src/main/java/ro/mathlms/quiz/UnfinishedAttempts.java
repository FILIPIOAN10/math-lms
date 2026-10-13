package ro.mathlms.quiz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ro.mathlms.storage.FileService;

import java.io.IOException;
import java.util.List;

/**
 * The attempts a student started and never handed in, on a test that is going away. They hold no grade, and a student
 * cannot finish them once the test is unpublished (a draft is not takeable), so deleting the test takes them along:
 * their answers, requirement points, photo rows and - once the deletion is committed - the photo files.
 */
@Component
class UnfinishedAttempts {

    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final FileService fileService;
    private final String quizPhotosDir;

    UnfinishedAttempts(QuizAttemptRepository attemptRepository, ItemResponseRepository responseRepository,
                       FileService fileService, @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir) {
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.fileService = fileService;
        this.quizPhotosDir = quizPhotosDir;
    }

    /** Deletes this quiz's in-progress attempts; returns how many there were. Call inside the deleting transaction. */
    int discard(Long quizId) {
        List<QuizAttempt> attempts = attemptRepository.findByQuizIdAndStatus(quizId, QuizAttemptStatus.IN_PROGRESS);
        for (QuizAttempt attempt : attempts) {
            List<ItemResponse> responses = responseRepository.findByAttemptId(attempt.getId());
            List<String> files = responses.stream()
                    .flatMap(r -> r.getPhotos().stream())
                    .map(ResponsePhoto::getImageKey)
                    .toList();
            responseRepository.deleteAll(responses); // photos and requirement points go with each response
            files.forEach(this::deleteFileAfterCommit);
        }
        attemptRepository.deleteAll(attempts);
        return attempts.size();
    }

    /** A rollback keeps the rows - and so their files. */
    private void deleteFileAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            fileService.deleteImage(quizPhotosDir, key);
        } catch (IOException ignored) {
            // A leftover file is not worth failing the deletion for.
        }
    }
}
