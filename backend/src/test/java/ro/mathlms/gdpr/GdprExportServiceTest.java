package ro.mathlms.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GdprExportServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
    private final ItemResponseRepository responseRepository = mock(ItemResponseRepository.class);
    private final FileService fileService = mock(FileService.class);
    private final GdprExportService service = new GdprExportService(
            userRepository, enrollmentRepository, attemptRepository, responseRepository,
            fileService, new ObjectMapper(), "uploads/quiz-photos");

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void exportsProfileEnrollmentsAttemptsAndPhotos() throws Exception {
        User student = withId(new User("elev@scoala.ro", "Elev Pop", Role.STUDENT), 1L);
        SchoolClass ninth = withId(new SchoolClass("Clasa a 9-a", null), 20L);
        Enrollment enrollment = withId(new Enrollment(student, ninth), 30L);

        Quiz quiz = withId(new Quiz("Simulare EN", null), 10L);
        QuizItem grila = withId(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 5, null), 100L);
        QuizOption four = withId(new QuizOption(grila, 0, "4", true), 1000L);
        QuizItem deschis = withId(new QuizItem(quiz, 2, QuizItemType.OPEN, "Rezolvă.", 30, null), 101L);
        QuizAttempt attempt = withId(new QuizAttempt(quiz, student), 50L);
        attempt.submit();

        ItemResponse r1 = new ItemResponse(attempt, grila);
        r1.answerSingleChoice(four);
        r1.gradeAuto(true, 5);
        ItemResponse r2 = new ItemResponse(attempt, deschis);
        r2.answerOpen("photo.jpg");
        r2.gradeManual(20, "Raționament corect.");
        attempt.commentOverall("Lucrare bună.");

        when(userRepository.findByEmail("elev@scoala.ro")).thenReturn(Optional.of(student));
        when(enrollmentRepository.findByStudentId(1L)).thenReturn(List.of(enrollment));
        when(attemptRepository.findByStudentIdOrderByStartedAtDesc(1L)).thenReturn(List.of(attempt));
        when(responseRepository.findByAttemptId(50L)).thenReturn(List.of(r1, r2));
        when(fileService.loadImage(eq("uploads/quiz-photos"), eq("photo.jpg")))
                .thenReturn(new ByteArrayResource("IMG".getBytes()));

        Map<String, byte[]> entries = unzip(service.exportZip("elev@scoala.ro"));

        assertThat(entries).containsKey("manifest.json");
        String manifest = new String(entries.get("manifest.json"));
        assertThat(manifest)
                .contains("elev@scoala.ro")
                .contains("Elev Pop")
                .contains("Clasa a 9-a")
                .contains("Simulare EN")
                .contains("\"4\"")
                // the teacher's comments are about the student too: they go into the archive
                .contains("Raționament corect.")
                .contains("Lucrare bună.");
        assertThat(entries).containsKey("photos/photo.jpg");
        assertThat(new String(entries.get("photos/photo.jpg"))).isEqualTo("IMG");
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                out.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return out;
    }
}
