package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EnrollmentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private UserRepository userRepository;

    private User student(String email) {
        return userRepository.save(new User(email, "Elev " + email, Role.STUDENT));
    }

    @Test
    void findsEnrollmentsByClassAndByStudent() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        SchoolClass tenth = schoolClassRepository.save(new SchoolClass("Clasa a 10-a", null));
        User ana = student("ana@scoala.ro");
        User dan = student("dan@scoala.ro");
        enrollmentRepository.save(new Enrollment(ana, ninth));
        enrollmentRepository.save(new Enrollment(dan, ninth));
        enrollmentRepository.save(new Enrollment(ana, tenth));

        List<Enrollment> ninthRoster = enrollmentRepository.findBySchoolClassId(ninth.getId());
        List<Enrollment> anaClasses = enrollmentRepository.findByStudentId(ana.getId());

        assertThat(ninthRoster).extracting(e -> e.getStudent().getId())
                .containsExactlyInAnyOrder(ana.getId(), dan.getId());
        assertThat(anaClasses).extracting(e -> e.getSchoolClass().getId())
                .containsExactlyInAnyOrder(ninth.getId(), tenth.getId());
    }

    @Test
    void enforcesOneEnrollmentPerStudentAndClass() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        User ana = student("ana@scoala.ro");
        enrollmentRepository.saveAndFlush(new Enrollment(ana, ninth));

        assertThatThrownBy(() ->
                enrollmentRepository.saveAndFlush(new Enrollment(ana, ninth)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByStudentAndClassReflectsPersistedRows() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        User ana = student("ana@scoala.ro");
        enrollmentRepository.saveAndFlush(new Enrollment(ana, ninth));

        assertThat(enrollmentRepository
                .existsByStudentIdAndSchoolClassId(ana.getId(), ninth.getId())).isTrue();
    }
}
