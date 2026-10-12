package ro.mathlms.parent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.StudentQuizDtos;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentServiceTest {

    private static final String PARENT_EMAIL = "maria@scoala.ro";
    private static final String CHILD_EMAIL = "ana@scoala.ro";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final QuizAttemptService quizAttemptService = mock(QuizAttemptService.class);
    private final ro.mathlms.assignment.AssignmentService assignmentService = mock(ro.mathlms.assignment.AssignmentService.class);
    private final ro.mathlms.mastery.MasteryService masteryService = mock(ro.mathlms.mastery.MasteryService.class);
    private final ro.mathlms.review.ReviewService reviewService = mock(ro.mathlms.review.ReviewService.class);
    private final ParentService service =
            new ParentService(userRepository, quizAttemptService, assignmentService, masteryService, reviewService);

    private final User parent = withId(new User(PARENT_EMAIL, "Maria Pop", Role.PARENT), 1L);
    private final User child = withId(new User(CHILD_EMAIL, "Ana Pop", Role.STUDENT), 2L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private MyAttemptDto attempt(long id) {
        return new MyAttemptDto(id, 10L, "Simulare EN", QuizAttemptStatus.GRADED, Instant.now(), Instant.now(), 13, AttemptMode.TEST, 35, false);
    }

    // --- children ---

    @Test
    void myChildrenAreTheStudentsLinkedToThisParent() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByParentIdOrderByFullName(1L)).thenReturn(List.of(child));

        assertThat(service.myChildren(PARENT_EMAIL)).containsExactly(child);
    }

    @Test
    void aParentWithoutChildrenGetsAnEmptyList() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByParentIdOrderByFullName(1L)).thenReturn(List.of());

        assertThat(service.myChildren(PARENT_EMAIL)).isEmpty();
    }

    @Test
    void anUnknownParentAccountIsRefused() {
        when(userRepository.findByEmail("fantoma@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myChildren("fantoma@scoala.ro"))
                .isInstanceOf(ParentAccessException.class);
    }

    // --- attempts ---

    @Test
    void anOwnChildsAttemptsAreLoadedThroughTheChildsOwnEmail() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        when(quizAttemptService.listMyAttempts(CHILD_EMAIL)).thenReturn(List.of(attempt(50L)));

        assertThat(service.childAttempts(PARENT_EMAIL, 2L)).extracting(MyAttemptDto::attemptId).containsExactly(50L);
    }

    @Test
    void someoneElsesChildIsRefusedBeforeAnyAttemptIsLoaded() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.childAttempts(PARENT_EMAIL, 99L))
                .isInstanceOf(ParentAccessException.class);
        verify(quizAttemptService, never()).listMyAttempts(anyString());
    }

    // --- progress ---

    @Test
    void anOwnChildsProgressIsLoadedThroughTheChildsOwnEmail() {
        StudentQuizDtos.ProgressPointDto point =
                new StudentQuizDtos.ProgressPointDto(50L, 10L, "Simulare EN", Instant.now(), 13, 15, 87);
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        when(quizAttemptService.getProgress(CHILD_EMAIL)).thenReturn(List.of(point));

        assertThat(service.childProgress(PARENT_EMAIL, 2L)).containsExactly(point);
    }

    @Test
    void someoneElsesChildsProgressIsRefused() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.childProgress(PARENT_EMAIL, 99L))
                .isInstanceOf(ParentAccessException.class);
        verify(quizAttemptService, never()).getProgress(anyString());
    }

    // --- result ---

    @Test
    void aChildsResultIsShownWhenTheAttemptIsTheirs() {
        AttemptResultViewDto view = mock(AttemptResultViewDto.class);
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        when(quizAttemptService.listMyAttempts(CHILD_EMAIL)).thenReturn(List.of(attempt(50L)));
        when(quizAttemptService.getResult(50L, CHILD_EMAIL)).thenReturn(view);

        assertThat(service.childResult(PARENT_EMAIL, 2L, 50L)).isSameAs(view);
    }

    @Test
    void anAttemptOfAnotherStudentIsRefusedEvenWithTheOwnChildsIdInTheUrl() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        when(quizAttemptService.listMyAttempts(CHILD_EMAIL)).thenReturn(List.of(attempt(50L)));

        assertThatThrownBy(() -> service.childResult(PARENT_EMAIL, 2L, 777L))
                .isInstanceOf(ParentAccessException.class);
        verify(quizAttemptService, never()).getResult(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    // --- homework ---

    @Test
    void anOwnChildsHomeworkIsLoadedThroughTheChildsOwnEmail() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        when(assignmentService.myAssignments(CHILD_EMAIL)).thenReturn(List.of());

        assertThat(service.childAssignments(PARENT_EMAIL, 2L)).isEmpty();
        verify(assignmentService).myAssignments(CHILD_EMAIL);
    }

    @Test
    void someoneElsesChildsHomeworkIsRefused() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(9L, 1L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.childAssignments(PARENT_EMAIL, 9L))
                .isInstanceOf(ParentAccessException.class);
        org.mockito.Mockito.verifyNoInteractions(assignmentService);
    }

    @Test
    void anOwnChildsMasteryIsComputedForThatChildOnly() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        when(masteryService.forStudent(CHILD_EMAIL)).thenReturn(List.of());

        assertThat(service.childMastery(PARENT_EMAIL, 2L)).isEmpty();

        when(userRepository.findByIdAndParentId(9L, 1L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.childMastery(PARENT_EMAIL, 9L))
                .isInstanceOf(ParentAccessException.class);
    }

    @Test
    void anOwnChildsReviewStatusIsLoadedForThatChildOnly() {
        when(userRepository.findByEmail(PARENT_EMAIL)).thenReturn(Optional.of(parent));
        when(userRepository.findByIdAndParentId(2L, 1L)).thenReturn(Optional.of(child));
        ro.mathlms.review.ReviewDtos.ReviewStatusDto status = new ro.mathlms.review.ReviewDtos.ReviewStatusDto(1, 0, 3, 5, 1, null);
        when(reviewService.status(2L)).thenReturn(status);

        assertThat(service.childReview(PARENT_EMAIL, 2L)).isSameAs(status);

        when(userRepository.findByIdAndParentId(9L, 1L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.childReview(PARENT_EMAIL, 9L))
                .isInstanceOf(ParentAccessException.class);
    }
}
