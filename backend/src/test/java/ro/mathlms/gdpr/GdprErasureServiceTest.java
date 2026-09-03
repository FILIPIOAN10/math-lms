package ro.mathlms.gdpr;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GdprErasureServiceTest {

    @Autowired private GdprErasureService service;
    @Autowired private UserRepository userRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository quizItemRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private VerificationTokenService tokenService;
    @MockitoBean private EmailService emailService;

    private record Seed(Long userId, Long attemptId, Long responseId) {}

    private Seed seedStudentWithData(String email) {
        User user = new User(email, "Elev Pop", Role.STUDENT);
        user.setPassword(passwordEncoder.encode("parola123"));
        user = userRepository.save(user);

        // @SpringBootTest doesn't roll back between tests, and school_classes.name is unique —
        // so key the class on the email to keep each test's seed independent.
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa " + email, null));
        enrollmentRepository.save(new Enrollment(user, ninth));

        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem open = quizItemRepository.save(
                new QuizItem(quiz, 1, QuizItemType.OPEN, "Rezolvă.", 30, null));
        QuizAttempt attempt = attemptRepository.save(new QuizAttempt(quiz, user));
        attempt.submit();
        attemptRepository.save(attempt);

        ItemResponse response = new ItemResponse(attempt, open);
        response.answerOpen("rezolvare.jpg");
        response = responseRepository.save(response);

        return new Seed(user.getId(), attempt.getId(), response.getId());
    }

    @Test
    void confirmErasureAnonymisesUserDeletesEnrollmentsAndPhotosButKeepsAttempts() {
        Seed seed = seedStudentWithData("elev@scoala.ro");
        String token = tokenService.generate("elev@scoala.ro", TokenPurpose.ERASE_ACCOUNT);

        service.confirmErasure(token);

        User erased = userRepository.findById(seed.userId()).orElseThrow();
        assertThat(erased.isErased()).isTrue();
        assertThat(erased.getErasedAt()).isNotNull();
        assertThat(erased.getEmail()).startsWith("erased-").endsWith("@erased.invalid");
        assertThat(erased.getPassword()).isNull();
        assertThat(erased.isEmailVerified()).isFalse();
        assertThat(userRepository.findByEmail("elev@scoala.ro")).isEmpty();

        // Personal state gone.
        assertThat(enrollmentRepository.findByStudentId(seed.userId())).isEmpty();
        // Grades kept, but the photo detached and its key cleared.
        assertThat(attemptRepository.findById(seed.attemptId())).isPresent();
        assertThat(responseRepository.findById(seed.responseId()).orElseThrow().getImageKey()).isNull();
    }

    @Test
    void confirmErasureIsIdempotent() {
        seedStudentWithData("bis@scoala.ro");
        // Two valid tokens issued before erasure (tokens are single-use, so we can't replay one).
        String token1 = tokenService.generate("bis@scoala.ro", TokenPurpose.ERASE_ACCOUNT);
        String token2 = tokenService.generate("bis@scoala.ro", TokenPurpose.ERASE_ACCOUNT);

        service.confirmErasure(token1);
        // A second valid token for an already-erased account is a quiet no-op (email no longer resolves).
        service.confirmErasure(token2);
    }

    @Test
    void requestErasureRejectsWrongPassword() {
        seedStudentWithData("wrong@scoala.ro");

        assertThatThrownBy(() -> service.requestErasure("wrong@scoala.ro", "gresita"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void requestErasureEmailsConfirmationOnCorrectPassword() {
        seedStudentWithData("ok@scoala.ro");

        service.requestErasure("ok@scoala.ro", "parola123");

        verify(emailService).sendErasureConfirmationEmail(org.mockito.ArgumentMatchers.eq("ok@scoala.ro"),
                org.mockito.ArgumentMatchers.anyString());
    }
}
