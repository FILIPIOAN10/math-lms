package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContentAccessTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final ChapterRepository chapterRepository = mock(ChapterRepository.class);
    private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
    private final ContentAccess access = new ContentAccess(
            userRepository, enrollmentRepository, bookRepository, chapterRepository, exerciseRepository);

    private final User ana = withId(new User("ana@scoala.ro", "Ana", Role.STUDENT), 7L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Authentication as(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private final Authentication student = as("ana@scoala.ro", "ROLE_STUDENT");

    private void anaIsEnrolledIn(long classId, boolean enrolled) {
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.of(ana));
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(7L, classId)).thenReturn(enrolled);
    }

    // --- class ---

    @Test
    void anEnrolledStudentMayReadTheirClass() {
        anaIsEnrolledIn(5L, true);

        assertThatCode(() -> access.checkClass(5L, student)).doesNotThrowAnyException();
    }

    @Test
    void aStudentMayNotReadAClassTheyAreNotIn() {
        anaIsEnrolledIn(5L, false);

        assertThatThrownBy(() -> access.checkClass(5L, student))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void anAdminIsNotRestricted() {
        assertThatCode(() -> access.checkClass(5L, as("prof@scoala.ro", "ROLE_ADMIN"))).doesNotThrowAnyException();
        verifyNoInteractions(userRepository, enrollmentRepository);
    }

    // --- parent: only the classes their children attend ---

    private final User maria = withId(new User("maria@scoala.ro", "Maria", Role.PARENT), 3L);
    private final Authentication parent = as("maria@scoala.ro", "ROLE_PARENT");

    private void aChildOfMariaIsEnrolledIn(long classId, boolean enrolled) {
        when(userRepository.findByEmail("maria@scoala.ro")).thenReturn(Optional.of(maria));
        when(enrollmentRepository.existsByStudentParentIdAndSchoolClassId(3L, classId)).thenReturn(enrolled);
    }

    @Test
    void aParentMayReadAClassOneOfTheirChildrenIsIn() {
        aChildOfMariaIsEnrolledIn(5L, true);

        assertThatCode(() -> access.checkClass(5L, parent)).doesNotThrowAnyException();
    }

    @Test
    void aParentMayNotReadAClassNoChildOfTheirsIsIn() {
        aChildOfMariaIsEnrolledIn(5L, false);

        assertThatThrownBy(() -> access.checkClass(5L, parent)).isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void aParentReadsBooksChaptersAndExercisesOnlyThroughTheirChildrensClasses() {
        when(bookRepository.findClassIdById(11L)).thenReturn(Optional.of(5L));
        when(chapterRepository.findClassIdById(21L)).thenReturn(Optional.of(5L));
        when(exerciseRepository.findClassIdById(31L)).thenReturn(Optional.of(5L));
        aChildOfMariaIsEnrolledIn(5L, false);

        assertThatThrownBy(() -> access.checkBook(11L, parent)).isInstanceOf(ContentNotFoundException.class);
        assertThatThrownBy(() -> access.checkChapter(21L, parent)).isInstanceOf(ContentNotFoundException.class);
        assertThatThrownBy(() -> access.checkExercise(31L, parent)).isInstanceOf(ContentNotFoundException.class);

        aChildOfMariaIsEnrolledIn(5L, true);
        assertThatCode(() -> access.checkBook(11L, parent)).doesNotThrowAnyException();
    }

    @Test
    void isRestrictedCoversStudentsAndParentsButNotAdmins() {
        assertThat(access.isRestricted(student)).isTrue();
        assertThat(access.isRestricted(parent)).isTrue();
        assertThat(access.isRestricted(as("prof@scoala.ro", "ROLE_ADMIN"))).isFalse();
    }

    @Test
    void aStudentWithoutAnAccountRowGetsNothing() {
        when(userRepository.findByEmail("ana@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.checkClass(5L, student))
                .isInstanceOf(ContentNotFoundException.class);
    }

    // --- book / chapter / exercise resolve to their class first ---

    @Test
    void aBookIsReadableOnlyThroughItsClass() {
        when(bookRepository.findClassIdById(11L)).thenReturn(Optional.of(5L));
        anaIsEnrolledIn(5L, true);
        assertThatCode(() -> access.checkBook(11L, student)).doesNotThrowAnyException();

        anaIsEnrolledIn(5L, false);
        assertThatThrownBy(() -> access.checkBook(11L, student)).isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void aChapterIsReadableOnlyThroughItsBooksClass() {
        when(chapterRepository.findClassIdById(21L)).thenReturn(Optional.of(5L));
        anaIsEnrolledIn(5L, false);

        assertThatThrownBy(() -> access.checkChapter(21L, student)).isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void anExerciseIsReadableOnlyThroughItsChaptersClass() {
        when(exerciseRepository.findClassIdById(31L)).thenReturn(Optional.of(5L));
        anaIsEnrolledIn(5L, true);

        assertThatCode(() -> access.checkExercise(31L, student)).doesNotThrowAnyException();
    }

    @Test
    void aMissingBookIsNotFoundForAStudent() {
        when(bookRepository.findClassIdById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.checkBook(404L, student)).isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void adminsSkipTheClassLookupForBooks() {
        assertThatCode(() -> access.checkBook(11L, as("prof@scoala.ro", "ROLE_ADMIN"))).doesNotThrowAnyException();
        verifyNoInteractions(bookRepository);
    }

    @Test
    void isStudentReadsTheRoleAuthority() {
        assertThat(access.isStudent(student)).isTrue();
        assertThat(access.isStudent(as("prof@scoala.ro", "ROLE_ADMIN"))).isFalse();
    }
}
