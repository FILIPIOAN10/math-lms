package ro.mathlms.gdpr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ro.mathlms.auth.EmailService;
import ro.mathlms.auth.RefreshTokenService;
import ro.mathlms.auth.TokenPurpose;
import ro.mathlms.auth.VerificationTokenService;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.invite.InviteLinkRepository;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.ResponsePhotoRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may erase themselves (the last admin may not; staff hand over their classes first) and when the photo files go
 * (only after the commit). Plain mocks: the rules depend on counts a shared test database cannot pin down.
 */
class GdprErasureRulesTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final VerificationTokenService tokens = mock(VerificationTokenService.class);
    private final ResponsePhotoRepository photos = mock(ResponsePhotoRepository.class);
    private final FileService files = mock(FileService.class);
    private final SchoolClassRepository classes = mock(SchoolClassRepository.class);
    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final InviteLinkRepository links = mock(InviteLinkRepository.class);

    private final GdprErasureService service = new GdprErasureService(users, encoder, tokens, mock(EmailService.class),
            mock(EnrollmentRepository.class), mock(ItemResponseRepository.class), photos,
            mock(QuizAttemptRepository.class), files, mock(RefreshTokenService.class), "quiz-photos",
            links, Clock.systemUTC(), classes, quizzes);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private User account(long id, Role role) {
        User user = User.registerLocal("u" + id + "@scoala.ro", "Nume", "HASH", role);
        user.verifyEmail();
        user.approve(role);
        ReflectionTestUtils.setField(user, "id", id);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(encoder.matches("parola", "HASH")).thenReturn(true);
        return user;
    }

    @Test
    void theLastAdminCannotLeave() {
        User admin = account(1, Role.ADMIN);
        when(users.findByRoleAndStatusAndErasedFalse(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> service.requestErasure(admin.getEmail(), "parola"))
                .isInstanceOf(ErasureNotAllowedException.class).hasMessageContaining("singurul administrator");
    }

    @Test
    void anAdminWithAColleagueAndNothingOwnedMayLeave() {
        User admin = account(1, Role.ADMIN);
        User colleague = account(2, Role.ADMIN);
        when(users.findByRoleAndStatusAndErasedFalse(Role.ADMIN, AccountStatus.ACTIVE)).thenReturn(List.of(admin, colleague));

        assertThatCode(() -> service.requestErasure(admin.getEmail(), "parola")).doesNotThrowAnyException();
    }

    @Test
    void aTeacherWhoStillOwnsAClassHandsItOverFirst() {
        User teacher = account(3, Role.TEACHER);
        when(classes.existsByTeacherId(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.requestErasure(teacher.getEmail(), "parola"))
                .isInstanceOf(ErasureNotAllowedException.class).hasMessageContaining("clase sau teste");

        when(classes.existsByTeacherId(3L)).thenReturn(false);
        when(quizzes.existsByTeacherId(3L)).thenReturn(true);
        assertThatThrownBy(() -> service.requestErasure(teacher.getEmail(), "parola"))
                .isInstanceOf(ErasureNotAllowedException.class);
    }

    @Test
    void photoFilesAreDeletedOnlyAfterTheCommit() throws Exception {
        User student = account(4, Role.STUDENT);
        when(tokens.verify("t", TokenPurpose.ERASE_ACCOUNT)).thenReturn(student.getEmail());
        when(photos.findImageKeysByStudentId(4L)).thenReturn(List.of("a.jpg", "b.jpg"));
        when(links.findOpenForStudent(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();

        service.confirmErasure("t");

        verify(files, never()).deleteImage(anyString(), anyString()); // still inside the transaction
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(files).deleteImage("quiz-photos", "a.jpg");
        verify(files).deleteImage("quiz-photos", "b.jpg");
    }

    @Test
    void aRolledBackErasureKeepsThePhotoFiles() throws Exception {
        User student = account(5, Role.STUDENT);
        when(tokens.verify("t", TokenPurpose.ERASE_ACCOUNT)).thenReturn(student.getEmail());
        when(photos.findImageKeysByStudentId(5L)).thenReturn(List.of("c.jpg"));
        when(links.findOpenForStudent(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();

        service.confirmErasure("t");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(files, never()).deleteImage(anyString(), anyString());
    }
}
