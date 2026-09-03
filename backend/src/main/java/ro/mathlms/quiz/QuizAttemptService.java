package ro.mathlms.quiz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentItemDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentQuizDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The student side of quizzes: browse published quizzes, start (or resume) an attempt, record
 * single-choice answers, and submit. On submit the SINGLE_CHOICE items are graded automatically
 * on the server; OPEN items are left for a teacher (Faza Q7). The correct answer is never sent to
 * the client before grading — see {@link StudentQuizDtos}.
 */
@Service
public class QuizAttemptService {

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final String quizPhotosDir;

    public QuizAttemptService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                              QuizOptionRepository optionRepository, QuizAttemptRepository attemptRepository,
                              ItemResponseRepository responseRepository, UserRepository userRepository,
                              FileService fileService,
                              @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir) {
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.quizPhotosDir = quizPhotosDir;
    }

    /** Published quizzes a student may take. */
    public List<Quiz> listPublished() {
        return quizRepository.findByStatusOrderByTitle(QuizStatus.PUBLISHED);
    }

    /**
     * Starts a fresh attempt at a published quiz, or resumes the student's existing in-progress one,
     * and returns the answer-hidden quiz to fill in.
     */
    @Transactional
    public StartedAttemptDto startAttempt(Long quizId, String studentEmail) {
        User student = requireUser(studentEmail);
        Quiz quiz = quizRepository.findById(quizId)
                .filter(q -> q.getStatus() == QuizStatus.PUBLISHED)
                .orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
        QuizAttempt attempt = attemptRepository
                .findByQuizIdAndStudentIdAndStatus(quizId, student.getId(), QuizAttemptStatus.IN_PROGRESS)
                .orElseGet(() -> attemptRepository.save(new QuizAttempt(quiz, student)));
        return new StartedAttemptDto(attempt.getId(), attempt.getStatus(), studentQuiz(quiz));
    }

    /** Records (or replaces) the student's choice for one SINGLE_CHOICE item. */
    @Transactional
    public void saveResponse(Long attemptId, Long itemId, Long optionId, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            throw new InvalidQuizException("Only single-choice items are answered this way");
        }
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
        if (!option.getItem().getId().equals(itemId)) {
            throw new InvalidQuizException("Option does not belong to this item");
        }
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        response.answerSingleChoice(option);
        responseRepository.save(response);
    }

    /**
     * Stores the uploaded rezolvare photo for one OPEN item and points the response at it. Replacing
     * an earlier photo deletes the old file. The correct/points stay null — a teacher grades it later.
     */
    @Transactional
    public void uploadOpenPhoto(Long attemptId, Long itemId, MultipartFile file, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        if (item.getType() != QuizItemType.OPEN) {
            throw new InvalidQuizException("Only open items take a photo");
        }
        requireImage(file);

        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        String previous = response.getImageKey();
        String stored;
        try {
            stored = fileService.uploadImage(quizPhotosDir, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the photo", e);
        }
        response.answerOpen(stored);
        responseRepository.save(response);

        if (previous != null) {
            try {
                fileService.deleteImage(quizPhotosDir, previous);
            } catch (IOException ignored) {
                // The new photo is saved; a leftover old file is not worth failing the request.
            }
        }
    }

    /**
     * Hands the attempt in. Every SINGLE_CHOICE response is auto-graded (correct earns the item's
     * points, anything else zero). If the quiz has no OPEN items the attempt is fully graded and its
     * final score set; otherwise it stays SUBMITTED until a teacher scores the open items.
     */
    @Transactional
    public AttemptResultDto submit(Long attemptId, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> byItem = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int autoScore = 0;
        int autoMaxScore = 0;
        boolean hasOpenItems = false;
        for (QuizItem item : items) {
            if (item.getType() != QuizItemType.SINGLE_CHOICE) {
                hasOpenItems = true;
                continue;
            }
            autoMaxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            boolean correct = response != null && response.getSelectedOption() != null
                    && response.getSelectedOption().isCorrect();
            int awarded = correct ? item.getPoints() : 0;
            if (response != null) {
                response.gradeAuto(correct, awarded);
                responseRepository.save(response);
            }
            autoScore += awarded;
        }

        attempt.submit();
        if (!hasOpenItems) {
            attempt.markGraded(autoScore);
        }
        attemptRepository.save(attempt);
        return new AttemptResultDto(attempt.getId(), attempt.getStatus(),
                autoScore, autoMaxScore, attempt.getScore());
    }

    private StudentQuizDto studentQuiz(Quiz quiz) {
        List<StudentItemDto> items = itemRepository.findByQuizIdOrderByPosition(quiz.getId()).stream()
                .map(item -> StudentItemDto.from(item, optionRepository.findByItemIdOrderByPosition(item.getId())))
                .toList();
        return StudentQuizDto.of(quiz, items);
    }

    private QuizAttempt requireOwnedInProgress(Long attemptId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getStudent().getEmail().equals(studentEmail)) {
            throw new QuizAccessException("This attempt belongs to another student");
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("This attempt is no longer in progress");
        }
        return attempt;
    }

    private static void requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidQuizException("A photo file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidQuizException("Only image files are accepted");
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new QuizAccessException("Unknown user " + email));
    }
    /** Fetch pentru poza uploadată, folosit de admin/profesor. */
    public Resource getOpenPhotoResource(Long attemptId, Long itemId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));

        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseThrow(() -> new InvalidQuizException("Nu există răspuns pentru acest subiect"));

        if (response.getImageKey() == null) {
            throw new InvalidQuizException("Nu a fost încărcată nicio poză pentru acest răspuns");
        }

        try {
            return fileService.loadImage(quizPhotosDir, response.getImageKey());
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut citi poza", e);
        }
    }

    /** Profesorul acordă punctaj manual pentru un subiect OPEN. */
    @Transactional
    public void gradeOpenResponse(Long attemptId, Long itemId, int points) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));

        if (attempt.getStatus() != QuizAttemptStatus.SUBMITTED) {
            throw new InvalidQuizException("Attempt-ul trebuie să fie SUBMITTED pentru corectură");
        }

        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));

        // Dacă elevul nu a răspuns nimic, creăm un răspuns gol ca să reținem punctajul (ex: 0)
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));

        response.gradeManual(points); // Va face throw automat dacă tipul nu e OPEN
        responseRepository.save(response);
    }

    /** Profesorul închide corectura, validând că toate subiectele OPEN au fost notate. */
    @Transactional
    public void finalizeGrading(Long attemptId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));

        if (attempt.getStatus() != QuizAttemptStatus.SUBMITTED) {
            throw new InvalidQuizException("Attempt-ul trebuie să fie SUBMITTED pentru finalizare");
        }

        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> responses = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int totalScore = 0;
        for (QuizItem item : items) {
            ItemResponse response = responses.get(item.getId());

            if (item.getType() == QuizItemType.OPEN) {
                if (response == null || response.getAwardedPoints() == null) {
                    throw new InvalidQuizException("Subiectul " + item.getId() + " nu a fost corectat!");
                }
            }

            if (response != null && response.getAwardedPoints() != null) {
                totalScore += response.getAwardedPoints();
            }
        }

        attempt.markGraded(totalScore);
        attemptRepository.save(attempt);
    }
}
