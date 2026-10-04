package ro.mathlms.gdpr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.auth.UserNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * GDPR Art. 15 — assembles everything the platform holds about a user and returns it as a ZIP
 * (a {@code manifest.json} plus the uploaded rezolvare photos). Synchronous: the data volume per
 * student is small, so there is no deferred/outbox machinery. Read-only.
 */
@Service
public class GdprExportService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final String quizPhotosDir;

    public GdprExportService(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                             QuizAttemptRepository attemptRepository, ItemResponseRepository responseRepository,
                             FileService fileService, ObjectMapper objectMapper,
                             @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
        this.quizPhotosDir = quizPhotosDir;
    }

    /** Builds the ZIP archive for the account identified by {@code email}. */
    @Transactional(readOnly = true)
    public byte[] exportZip(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No account for " + email));
        GdprExportData.Archive archive = assemble(user);
        return zip(user, archive);
    }

    private GdprExportData.Archive assemble(User user) {
        GdprExportData.Profile profile = new GdprExportData.Profile(
                user.getEmail(),
                user.getFullName(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus().name(),
                user.isEmailVerified(),
                user.getGoogleId() != null,
                user.getParent() == null ? null : user.getParent().getEmail());

        List<GdprExportData.EnrolledClass> enrollments =
                enrollmentRepository.findByStudentId(user.getId()).stream()
                        .map(Enrollment::getSchoolClass)
                        .map(c -> new GdprExportData.EnrolledClass(c.getName()))
                        .toList();

        List<GdprExportData.Attempt> attempts =
                attemptRepository.findByStudentIdOrderByStartedAtDesc(user.getId()).stream()
                        .map(this::toAttempt)
                        .toList();

        return new GdprExportData.Archive(Instant.now().toString(), profile, enrollments, attempts);
    }

    private GdprExportData.Attempt toAttempt(QuizAttempt attempt) {
        List<GdprExportData.ResponseEntry> responses =
                responseRepository.findByAttemptId(attempt.getId()).stream()
                        .map(this::toResponse)
                        .toList();
        return new GdprExportData.Attempt(
                attempt.getQuiz().getTitle(),
                attempt.getStatus().name(),
                attempt.getScore(),
                attempt.getStartedAt().toString(),
                Objects.toString(attempt.getSubmittedAt(), null),
                responses);
    }

    private GdprExportData.ResponseEntry toResponse(ItemResponse response) {
        return new GdprExportData.ResponseEntry(
                response.getItem().getPosition(),
                response.getItem().getType().name(),
                response.getItem().getStatement(),
                response.getSelectedOption() == null ? null : response.getSelectedOption().getText(),
                response.getImageKey(),
                response.getAwardedPoints(),
                response.getCorrect());
    }

    private byte[] zip(User user, GdprExportData.Archive archive) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(archive));
            zip.closeEntry();

            addPhotos(zip, archive);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the export manifest", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the export archive", e);
        }
        return buffer.toByteArray();
    }

    private void addPhotos(ZipOutputStream zip, GdprExportData.Archive archive) throws IOException {
        for (GdprExportData.Attempt attempt : archive.attempts()) {
            for (GdprExportData.ResponseEntry response : attempt.responses()) {
                String key = response.photoFile();
                if (key == null) {
                    continue;
                }
                Resource photo;
                try {
                    photo = fileService.loadImage(quizPhotosDir, key);
                } catch (IOException missing) {
                    // A photo referenced in the DB but gone from disk shouldn't break the whole export.
                    continue;
                }
                zip.putNextEntry(new ZipEntry("photos/" + key));
                try (InputStream in = photo.getInputStream()) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }
}
