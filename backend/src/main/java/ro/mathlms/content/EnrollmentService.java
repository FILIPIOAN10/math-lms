package ro.mathlms.content;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.Comparator;
import java.util.List;

/** Admin management of who is enrolled in which class. */
@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             SchoolClassRepository schoolClassRepository,
                             UserRepository userRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
    }

    public List<Enrollment> roster(Long classId) {
        requireClass(classId);
        return enrollmentRepository.findBySchoolClassIdFetchStudent(classId);
    }

    /** The classes the logged-in student attends, ordered by name — what the student may browse. */
    @Transactional(readOnly = true)
    public List<SchoolClass> myClasses(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ContentNotFoundException("No account for " + studentEmail));
        return enrollmentRepository.findByStudentIdFetchClass(student.getId()).stream()
                .map(Enrollment::getSchoolClass)
                .sorted(Comparator.comparing(SchoolClass::getName, NaturalOrder.BY_NAME))
                .toList();
    }

    /** The classes attended by the children linked to this parent — what the parent may browse. */
    @Transactional(readOnly = true)
    public List<SchoolClass> classesOfMyChildren(String parentEmail) {
        User parent = userRepository.findByEmail(parentEmail)
                .orElseThrow(() -> new ContentNotFoundException("No account for " + parentEmail));
        return enrollmentRepository.findClassesOfChildrenOf(parent.getId()).stream()
                .sorted(Comparator.comparing(SchoolClass::getName, NaturalOrder.BY_NAME))
                .toList();
    }

    @Transactional
    public Enrollment enroll(Long classId, Long studentId) {
        SchoolClass schoolClass = requireClass(classId);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ContentNotFoundException("Student", studentId));
        if (enrollmentRepository.existsByStudentIdAndSchoolClassId(studentId, classId)) {
            throw new DuplicateContentException("This student is already enrolled in this class");
        }
        // The Enrollment constructor enforces that the account's real role is STUDENT.
        return enrollmentRepository.save(new Enrollment(student, schoolClass));
    }

    @Transactional
    public void unenroll(Long enrollmentId) {
        if (!enrollmentRepository.existsById(enrollmentId)) {
            throw new ContentNotFoundException("Enrollment", enrollmentId);
        }
        enrollmentRepository.deleteById(enrollmentId);
    }

    private SchoolClass requireClass(Long classId) {
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", classId));
    }
}
