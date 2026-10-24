package ro.mathlms.quiz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

/**
 * The picture under a question's statement (EN Subiectul II figures). The teacher attaches, replaces or removes it; it
 * is served to whoever may see the question: the teacher, a student who may take the quiz or already has an attempt at
 * it, a parent whose child has an attempt. Anyone else gets the same 404 as for a question that does not exist.
 */
@Service
public class QuestionImageService {

    private static final Logger log = LoggerFactory.getLogger(QuestionImageService.class);

    /** The browser shrinks pictures before upload; this bounds what a direct call may store. */
    static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final QuizItemRepository itemRepository;
    private final QuizAttemptRepository attemptRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final QuizRepository quizRepository;
    private final AuditLog auditLog;
    private final String dir;

    public QuestionImageService(QuizItemRepository itemRepository, QuizAttemptRepository attemptRepository,
                                EnrollmentRepository enrollmentRepository, UserRepository userRepository,
                                FileService fileService, QuizRepository quizRepository, AuditLog auditLog,
                                @Value("${app.storage.question-images-dir}") String dir) {
        this.itemRepository = itemRepository;
        this.attemptRepository = attemptRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.quizRepository = quizRepository;
        this.auditLog = auditLog;
        this.dir = dir;
    }

    /** Like the question's other edits (see {@link QuizAdminService}): journalled once students can see the quiz. */
    private void record(AuditAction action, QuizItem item) {
        Quiz quiz = item.getQuiz();
        if (quiz.getStatus() == QuizStatus.PUBLISHED || quizRepository.countAttempts(quiz.getId()) > 0) {
            String number = QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(quiz.getId()));
            auditLog.record(AuditEvent.of(action, quiz.getId(), AuditText.question(quiz.getTitle(), number)));
        }
    }

    // --- teacher ---

    /** Puts the picture under the statement, replacing any earlier one. Allowed even once taken: it is content. */
    @Transactional
    public void attach(Long itemId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidQuizException("Alege o imagine.");
        }
        if (file.getContentType() == null || !TYPES.contains(file.getContentType())) {
            throw new InvalidQuizException("Imaginea trebuie să fie PNG, JPG, WEBP sau GIF.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidQuizException("Imaginea e prea mare (maxim 4 MB).");
        }
        QuizItem item = requireItem(itemId);
        String stored;
        try {
            stored = fileService.uploadImage(dir, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut salva imaginea", e);
        }
        String old = item.removeImage();
        item.attachImage(stored);
        itemRepository.save(item);
        deleteAfterCommit(old);
        // Should the transaction fail, the new file is not referenced by anything: remove it again.
        whenRolledBack(stored);
        record(AuditAction.QUESTION_IMAGE_SET, item);
    }

    @Transactional
    public void remove(Long itemId) {
        QuizItem item = requireItem(itemId);
        String removed = item.removeImage();
        deleteAfterCommit(removed);
        itemRepository.save(item);
        if (removed != null) {
            record(AuditAction.QUESTION_IMAGE_REMOVED, item);
        }
    }

    /** A stored copy of a picture, for a copied question (each question owns its file). Null in, null out. */
    public String copyOf(String imageKey) {
        if (imageKey == null) {
            return null;
        }
        try {
            String copy = fileService.copyImage(dir, imageKey);
            whenRolledBack(copy);
            return copy;
        } catch (IOException e) {
            log.warn("Could not copy question image {} - the copy goes without it", imageKey, e);
            return null;
        }
    }

    /** Deletes a question's file once its removal (or the question's) is committed. */
    public void deleteAfterCommit(String imageKey) {
        if (imageKey == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(imageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(imageKey);
            }
        });
    }

    // --- everyone who may see the question ---

    @Transactional(readOnly = true)
    public Resource load(Long itemId, String viewerEmail) {
        QuizItem item = requireItem(itemId);
        if (item.getImageKey() == null || !maySee(item.getQuiz(), viewerEmail)) {
            throw new QuizNotFoundException("QuizItem", itemId);
        }
        try {
            return fileService.loadImage(dir, item.getImageKey());
        } catch (IOException e) {
            throw new QuizNotFoundException("QuizItem", itemId);
        }
    }

    private boolean maySee(Quiz quiz, String email) {
        User viewer = userRepository.findByEmail(email).orElse(null);
        if (viewer == null || viewer.getRole() == null) {
            return false;
        }
        return switch (viewer.getRole()) {
            case ADMIN -> true;
            case TEACHER -> viewer.getId().equals(quiz.getTeacherId());
            case STUDENT -> attemptRepository.existsByStudentIdAndQuizId(viewer.getId(), quiz.getId())
                    || (quiz.getStatus() == QuizStatus.PUBLISHED && (quiz.getSchoolClass() == null
                        || enrollmentRepository.existsByStudentIdAndSchoolClassId(viewer.getId(), quiz.getSchoolClass().getId())));
            case PARENT -> {
                List<Long> children = userRepository.findByParentIdOrderByFullName(viewer.getId()).stream()
                        .map(User::getId).toList();
                yield !children.isEmpty() && attemptRepository.existsByStudentIdInAndQuizId(children, quiz.getId());
            }
        };
    }

    // --- helpers ---

    private QuizItem requireItem(Long itemId) {
        return itemRepository.findById(itemId).orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
    }

    private void whenRolledBack(String imageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    delete(imageKey);
                }
            }
        });
    }

    private void delete(String imageKey) {
        try {
            fileService.deleteImage(dir, imageKey);
        } catch (IOException e) {
            log.warn("Could not delete question image {}", imageKey, e);
        }
    }
}
