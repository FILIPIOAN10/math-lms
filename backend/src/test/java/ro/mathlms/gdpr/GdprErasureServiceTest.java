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
import ro.mathlms.invite.InviteLinkDtos.InviteLinkDto;
import ro.mathlms.invite.InviteLinkRepository;
import ro.mathlms.invite.InviteLinkService;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.ResponsePhotoRepository;
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
    @Autowired private ResponsePhotoRepository photoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private VerificationTokenService tokenService;
    @Autowired private InviteLinkService inviteLinkService;
    @Autowired private InviteLinkRepository inviteLinkRepository;
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
        attempt.commentOverall("Ana, ai progresat mult.");
        attemptRepository.save(attempt);

        ItemResponse response = new ItemResponse(attempt, open);
        response.addPhoto("rezolvare.jpg", java.time.Instant.EPOCH);
        response.gradeManual(25, "Ana, verifică semnul.");
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
        // Grades kept, but the photos gone (rows and files).
        assertThat(attemptRepository.findById(seed.attemptId())).isPresent();
        assertThat(photoRepository.findImageKeysByStudentId(seed.userId())).isEmpty();
        // Free text about the person may name them: the teacher's comments go too, the points stay.
        assertThat(attemptRepository.findById(seed.attemptId()).orElseThrow().getTeacherComment()).isNull();
        ItemResponse kept = responseRepository.findById(seed.responseId()).orElseThrow();
        assertThat(kept.getTeacherComment()).isNull();
        assertThat(kept.getAwardedPoints()).isEqualTo(25);
    }

    private User active(String email, Role role) {
        User user = User.registerLocal(email, "Nume " + role, "HASH", role);
        user.verifyEmail();
        user.approve(role);
        return userRepository.save(user);
    }

    @Test
    void erasingAParentUnlinksTheChildrenSoTheyCanGetANewParent() {
        User parent = active("parinte-sters@scoala.ro", Role.PARENT);
        User child = active("copil-ramas@scoala.ro", Role.STUDENT);
        child.linkParent(parent);
        userRepository.save(child);

        service.confirmErasure(tokenService.generate("parinte-sters@scoala.ro", TokenPurpose.ERASE_ACCOUNT));

        assertThat(userRepository.findById(child.getId()).orElseThrow().getParent()).isNull();
        assertThat(userRepository.findByParentIdOrderByFullName(parent.getId())).isEmpty();
        // the teacher can hand the child's new parent a link again (refused while the tombstone was linked)
        assertThat(inviteLinkService.createParentInvite(child.getId()).kind()).isNotNull();
    }

    @Test
    void erasingAStudentRevokesTheirUnusedParentLinks() {
        User student = active("elev-cu-link@scoala.ro", Role.STUDENT);
        InviteLinkDto link = inviteLinkService.createParentInvite(student.getId());

        service.confirmErasure(tokenService.generate("elev-cu-link@scoala.ro", TokenPurpose.ERASE_ACCOUNT));

        assertThat(inviteLinkRepository.findById(link.id()).orElseThrow().getRevokedAt()).isNotNull();
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

    @Test
    void aTeacherWhoStillOwnsAClassCannotEraseThemselvesUntilItIsHandedOver() {
        User teacher = active("prof-cu-clasa@scoala.ro", Role.TEACHER);
        teacher.setPassword(passwordEncoder.encode("parola123"));
        userRepository.save(teacher);
        SchoolClass owned = new SchoolClass("Clasa profului " + teacher.getId(), null);
        owned.assignTeacher(teacher.getId());
        owned = schoolClassRepository.save(owned);

        assertThatThrownBy(() -> service.requestErasure("prof-cu-clasa@scoala.ro", "parola123"))
                .isInstanceOf(ErasureNotAllowedException.class);
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.never())
                .sendErasureConfirmationEmail(org.mockito.ArgumentMatchers.eq("prof-cu-clasa@scoala.ro"), org.mockito.ArgumentMatchers.anyString());

        owned.assignTeacher(null); // an admin gave the class to someone else
        schoolClassRepository.save(owned);
        service.requestErasure("prof-cu-clasa@scoala.ro", "parola123");
        verify(emailService).sendErasureConfirmationEmail(org.mockito.ArgumentMatchers.eq("prof-cu-clasa@scoala.ro"),
                org.mockito.ArgumentMatchers.anyString());
    }
}
