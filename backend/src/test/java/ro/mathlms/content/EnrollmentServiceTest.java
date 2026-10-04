package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentServiceTest {

    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final SchoolClassRepository schoolClassRepository = mock(SchoolClassRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EnrollmentService service =
            new EnrollmentService(enrollmentRepository, schoolClassRepository, userRepository);

    private final SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);

    @Test
    void rosterThrowsWhenClassMissing() {
        when(schoolClassRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.roster(404L))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void enrollSavesTheEnrollment() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(ninth));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(2L, 1L)).thenReturn(false);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Enrollment created = service.enroll(1L, 2L);

        assertThat(created.getStudent()).isSameAs(student);
        assertThat(created.getSchoolClass()).isSameAs(ninth);
    }

    @Test
    void enrollThrowsWhenStudentMissing() {
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(ninth));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(1L, 404L))
                .isInstanceOf(ContentNotFoundException.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enrollRejectsDuplicate() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(ninth));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.enroll(1L, 2L))
                .isInstanceOf(DuplicateContentException.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enrollRejectsNonStudentAccount() {
        User parent = new User("parinte@scoala.ro", "Maria Pop", Role.PARENT);
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(ninth));
        when(userRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(enrollmentRepository.existsByStudentIdAndSchoolClassId(2L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.enroll(1L, 2L))
                .isInstanceOf(IllegalStateException.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void myClassesReturnsOnlyTheClassesOfTheStudentWithThatEmail() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        SchoolClass tenth = new SchoolClass("Clasa a 10-a", null);
        when(userRepository.findByEmail("elev@scoala.ro")).thenReturn(Optional.of(student));
        when(enrollmentRepository.findByStudentIdFetchClass(any()))
                .thenReturn(List.of(new Enrollment(student, ninth), new Enrollment(student, tenth)));

        List<SchoolClass> classes = service.myClasses("elev@scoala.ro");

        assertThat(classes).containsExactly(ninth, tenth);
    }

    @Test
    void myClassesComeInNaturalOrder() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        SchoolClass tenth = new SchoolClass("Clasa a 10-a", null);
        when(userRepository.findByEmail("elev@scoala.ro")).thenReturn(Optional.of(student));
        when(enrollmentRepository.findByStudentIdFetchClass(any()))
                .thenReturn(List.of(new Enrollment(student, tenth), new Enrollment(student, ninth)));

        assertThat(service.myClasses("elev@scoala.ro")).containsExactly(ninth, tenth);
    }

    @Test
    void myClassesIsEmptyForAStudentWithoutEnrollments() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        when(userRepository.findByEmail("elev@scoala.ro")).thenReturn(Optional.of(student));
        when(enrollmentRepository.findByStudentIdFetchClass(any())).thenReturn(List.of());

        assertThat(service.myClasses("elev@scoala.ro")).isEmpty();
    }

    @Test
    void myClassesThrowsWhenTheAccountDoesNotExist() {
        when(userRepository.findByEmail("fantoma@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myClasses("fantoma@scoala.ro"))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void classesOfMyChildrenAreTheClassesAnyLinkedChildAttends() {
        User parent = new User("maria@scoala.ro", "Maria Pop", Role.PARENT);
        when(userRepository.findByEmail("maria@scoala.ro")).thenReturn(Optional.of(parent));
        when(enrollmentRepository.findClassesOfChildrenOf(any())).thenReturn(List.of(ninth));

        assertThat(service.classesOfMyChildren("maria@scoala.ro")).containsExactly(ninth);
    }

    @Test
    void classesOfMyChildrenThrowsWhenTheAccountDoesNotExist() {
        when(userRepository.findByEmail("fantoma@scoala.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.classesOfMyChildren("fantoma@scoala.ro"))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void unenrollThrowsWhenMissing() {
        when(enrollmentRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.unenroll(404L))
                .isInstanceOf(ContentNotFoundException.class);
        verify(enrollmentRepository, never()).deleteById(any());
    }
}
